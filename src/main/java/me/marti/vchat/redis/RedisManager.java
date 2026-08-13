package me.marti.vchat.redis;

import com.google.gson.Gson;
import me.marti.vchat.VChat;
import me.marti.vchat.storage.StorageManager;
import me.marti.vchat.utils.PlatformUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.*;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Redis is transport/presence only; durable state remains in StorageManager. */
public final class RedisManager {
    private static final String CLEAR_PRESENCE = "if redis.call('get',KEYS[1])==ARGV[1] then redis.call('del',KEYS[1]); if redis.call('get',KEYS[2])==ARGV[2] then redis.call('del',KEYS[2]); end; return 1 else return 0 end";
    private static final String HEARTBEAT = "if redis.call('get',KEYS[1])==ARGV[1] then redis.call('expire',KEYS[1],ARGV[2]); redis.call('set',KEYS[2],ARGV[3],'EX',ARGV[2]); return 1 else return 0 end";
    private static final String PREPARE_QUIT = "if redis.call('get',KEYS[1])==ARGV[1] then redis.call('expire',KEYS[1],ARGV[3]); if redis.call('get',KEYS[2])==ARGV[2] then redis.call('expire',KEYS[2],ARGV[3]); end; return 1 else return 0 end";

    private final VChat plugin;
    private final ExecutorService io;
    private final Gson gson = new Gson();
    private final EventDeduplicator deduplicator = new EventDeduplicator(4096, 300_000L);
    private final Map<UUID, Presence> localPresence = new java.util.concurrent.ConcurrentHashMap<>();
    private final LifecycleGate lifecycle = new LifecycleGate();
    private JedisPool pool;
    private Thread subscriberThread;
    private volatile JedisPubSub subscriber;
    private volatile boolean enabled;
    private volatile boolean running;
    private String serverId;
    private String networkId;
    private String namespace;
    private String channel;
    private int presenceTtlSeconds;

    public RedisManager(VChat plugin, ExecutorService io) {
        this.plugin = plugin;
        this.io = io;
    }

    public CompletableFuture<Boolean> enable() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        if (!config.getBoolean("redis.enabled", false)) return CompletableFuture.completedFuture(false);
        if (!plugin.getStorageManager().isMysql()) {
            plugin.getLogger().warning("Cross-server disabled: redis.enabled requires storage.type=mysql.");
            return CompletableFuture.completedFuture(false);
        }
        if (!PlatformUtil.isPaper()) {
            plugin.getLogger().warning("Cross-server disabled: Redis is supported only on Paper.");
            return CompletableFuture.completedFuture(false);
        }
        this.serverId = config.getString("redis.server-id", "server1");
        this.networkId = config.getString("redis.network-id", "valerin");
        if (!RedisEnvelopeValidator.validId(serverId) || !RedisEnvelopeValidator.validId(networkId)) {
            plugin.getLogger().warning("Cross-server disabled: invalid network-id or server-id.");
            return CompletableFuture.completedFuture(false);
        }
        if (!lifecycle.start()) return CompletableFuture.completedFuture(enabled);
        this.namespace = namespace(networkId);
        this.channel = namespace + "events";
        this.presenceTtlSeconds = validatedTtlSeconds(config.getInt("redis.presence-ttl-seconds", 30));

        String host = config.getString("redis.host", "localhost");
        int port = config.getInt("redis.port", 6379);
        String user = config.getString("redis.user", "");
        String password = config.getString("redis.password", "");
        int database = config.getInt("redis.database", 0);
        boolean ssl = config.getBoolean("redis.use-ssl", false);

        return CompletableFuture.supplyAsync(() -> {
            try {
                JedisPoolConfig poolConfig = new JedisPoolConfig();
                poolConfig.setMaxTotal(6);
                poolConfig.setTestOnBorrow(true);
                DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                        .ssl(ssl).database(database).timeoutMillis(3000)
                        .user(user.isBlank() ? null : user).password(password.isBlank() ? null : password).build();
                pool = new JedisPool(poolConfig, new HostAndPort(host, port), clientConfig);
                try (Jedis jedis = pool.getResource()) { jedis.ping(); }
                running = true;
                enabled = true;
                subscriber = new Listener();
                subscriberThread = new Thread(this::subscribeLoop, "vchat:redis-subscriber");
                subscriberThread.setDaemon(true);
                subscriberThread.start();
                plugin.getLogger().info("Redis connected: network=" + networkId + " server=" + serverId + ".");
                return true;
            } catch (Exception error) {
                enabled = false;
                if (pool != null) pool.close();
                plugin.getLogger().warning("Redis unavailable; cross-server features disabled: " + safe(error));
                return false;
            } finally {
                if (!enabled) lifecycle.stop();
            }
        }, io);
    }

    public void disable() {
        running = false;
        enabled = false;
        JedisPubSub current = subscriber;
        if (current != null) try { current.unsubscribe(); } catch (Exception ignored) { }
        if (subscriberThread != null) subscriberThread.interrupt();
        if (pool != null) pool.close();
        localPresence.clear();
        lifecycle.stop();
    }

    public boolean isEnabled() { return enabled; }
    public String getServerId() { return serverId; }
    public String getNetworkId() { return networkId; }

    public boolean publish(RedisEvent event) {
        if (!enabled || event == null || event.type == null) return false;
        event.networkId = networkId;
        event.sourceServer = serverId;
        event.createdAt = System.currentTimeMillis();
        String json = gson.toJson(event);
        if (json.getBytes(StandardCharsets.UTF_8).length > RedisEnvelopeValidator.MAX_EVENT_BYTES) return false;
        io.execute(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, json);
            } catch (Exception error) {
                plugin.getLogger().warning("Redis publish failed: " + safe(error));
            }
        });
        return true;
    }

    public void registerPresence(UUID uuid, String name, Consumer<Boolean> callback) {
        if (!enabled) { onMain(() -> callback.accept(false)); return; }
        io.execute(() -> {
            boolean alreadyOnline = false;
            Presence presence = new Presence(serverId, UUID.randomUUID().toString(), uuid, name);
            try (Jedis jedis = pool.getResource()) {
                String normalized = StorageManager.normalizeName(name);
                String oldUuid = jedis.get(nameKey(normalized));
                Presence old = null;
                if (oldUuid != null) {
                    try { old = Presence.parse(jedis.get(presenceKey(UUID.fromString(oldUuid)))); }
                    catch (IllegalArgumentException corrupt) { jedis.del(nameKey(normalized)); }
                }
                alreadyOnline = old != null && old.playerId().equals(uuid);
                jedis.setex(presenceKey(uuid), presenceTtlSeconds, presence.encode());
                jedis.setex(nameKey(normalized), presenceTtlSeconds, uuid.toString());
                localPresence.put(uuid, presence);
            } catch (Exception error) {
                plugin.getLogger().warning("Redis presence update failed: " + safe(error));
            }
            boolean result = alreadyOnline;
            onMain(() -> callback.accept(result));
        });
    }

    public void heartbeat() {
        if (!enabled) return;
        io.execute(() -> {
            try (Jedis jedis = pool.getResource()) {
                for (Presence presence : localPresence.values()) {
                    jedis.eval(HEARTBEAT, List.of(presenceKey(presence.playerId()), nameKey(StorageManager.normalizeName(presence.playerName()))),
                            List.of(presence.encode(), String.valueOf(presenceTtlSeconds), presence.playerId().toString()));
                }
            } catch (Exception error) {
                plugin.debugLog("Redis heartbeat failed: " + safe(error));
            }
        });
    }

    public Presence beginQuit(UUID uuid) {
        Presence expected = localPresence.remove(uuid);
        if (enabled && expected != null) {
            int quitTtlSeconds = quitPresenceTtlSeconds(transferGraceSeconds());
            io.execute(() -> {
                try (Jedis jedis = pool.getResource()) {
                    jedis.eval(PREPARE_QUIT,
                            List.of(presenceKey(uuid), nameKey(StorageManager.normalizeName(expected.playerName()))),
                            List.of(expected.encode(), uuid.toString(), String.valueOf(quitTtlSeconds)));
                } catch (Exception error) {
                    plugin.debugLog("Redis quit grace refresh failed: " + safe(error));
                }
            });
        }
        return expected;
    }

    public long transferGraceTicks() {
        return transferGraceTicks(transferGraceSeconds());
    }

    public void clearPresence(Presence expected, Consumer<Boolean> callback) {
        if (!enabled || expected == null) { onMain(() -> callback.accept(false)); return; }
        UUID uuid = expected.playerId();
        io.execute(() -> {
            boolean cleared = false;
            try (Jedis jedis = pool.getResource()) {
                Object result = jedis.eval(CLEAR_PRESENCE,
                        List.of(presenceKey(uuid), nameKey(StorageManager.normalizeName(expected.playerName()))),
                        List.of(expected.encode(), uuid.toString()));
                cleared = Long.valueOf(1L).equals(result);
            } catch (Exception error) {
                plugin.debugLog("Redis delayed quit failed: " + safe(error));
            }
            boolean result = cleared;
            onMain(() -> callback.accept(result));
        });
    }

    public void findPlayer(String exactName, Consumer<Presence> callback) {
        if (!enabled) { onMain(() -> callback.accept(null)); return; }
        io.execute(() -> {
            Presence result = null;
            try (Jedis jedis = pool.getResource()) {
                String uuid = jedis.get(nameKey(StorageManager.normalizeName(exactName)));
                if (uuid != null) result = Presence.parse(jedis.get(presenceKey(UUID.fromString(uuid))));
                if (result != null && !result.playerName().equalsIgnoreCase(exactName)) result = null;
            } catch (Exception ignored) { }
            Presence found = result;
            onMain(() -> callback.accept(found));
        });
    }

    public void findPlayer(UUID uuid, Consumer<Presence> callback) {
        if (!enabled) { onMain(() -> callback.accept(null)); return; }
        io.execute(() -> {
            Presence result = null;
            try (Jedis jedis = pool.getResource()) { result = Presence.parse(jedis.get(presenceKey(uuid))); }
            catch (Exception ignored) { }
            Presence found = result;
            onMain(() -> callback.accept(found));
        });
    }

    public void networkOnlineCount(Consumer<Long> callback) {
        if (!enabled) { onMain(() -> callback.accept(-1L)); return; }
        io.execute(() -> {
            long count = scanPresenceCount();
            onMain(() -> callback.accept(count));
        });
    }

    public boolean claimTopicLeadership(String channelId, long ttlMillis) {
        if (!enabled || Bukkit.isPrimaryThread()) return false;
        try (Jedis jedis = pool.getResource()) {
            String key = namespace + "lock:discord-topic:" + channelId;
            String current = jedis.get(key);
            if (serverId.equals(current)) { jedis.pexpire(key, ttlMillis); return true; }
            return "OK".equals(jedis.set(key, serverId, SetParams.setParams().nx().px(ttlMillis)));
        } catch (Exception error) { return false; }
    }

    public long getNetworkOnlineCount() {
        return !enabled || Bukkit.isPrimaryThread() ? -1 : scanPresenceCount();
    }

    private long scanPresenceCount() {
        try (Jedis jedis = pool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            long count = 0;
            ScanParams params = new ScanParams().match(namespace + "presence:*").count(100);
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                count += result.getResult().size();
                cursor = result.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            return count;
        } catch (Exception error) { return -1; }
    }

    private void subscribeLoop() {
        long delay = 1000L;
        while (running && pool != null && !pool.isClosed()) {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(subscriber, channel);
                delay = 1000L;
            } catch (Exception error) {
                if (!running) return;
                plugin.getLogger().warning("Redis subscriber disconnected; retrying in " + delay + "ms.");
                try { Thread.sleep(delay); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                delay = Math.min(30_000L, delay * 2L);
            }
        }
    }

    private final class Listener extends JedisPubSub {
        @Override public void onMessage(String receivedChannel, String message) {
            int bytes = message == null ? 0 : message.getBytes(StandardCharsets.UTF_8).length;
            RedisEvent event;
            try { event = gson.fromJson(message, RedisEvent.class); }
            catch (Exception invalid) { plugin.getLogger().warning("Rejected malformed Redis event."); return; }
            long now = System.currentTimeMillis();
            if (!RedisEnvelopeValidator.valid(event, networkId, now, bytes)
                    || RedisEnvelopeValidator.loopback(event, serverId)
                    || !deduplicator.first(event.eventId, now)) return;
            onMain(() -> plugin.getRedisEventHandler().handle(event));
        }
    }

    static String namespace(String networkId) { return "valerin:" + networkId + ":vchat:"; }
    String presenceKey(UUID uuid) { return namespace + "presence:" + uuid; }
    String nameKey(String normalizedName) { return namespace + "name:" + normalizedName; }

    static boolean delayedQuitMatches(Presence current, Presence expected) {
        return current != null && expected != null && current.sessionId().equals(expected.sessionId())
                && current.serverId().equals(expected.serverId()) && current.playerId().equals(expected.playerId());
    }

    static int validatedTtlSeconds(int configured) { return Math.max(15, configured); }
    static long transferGraceTicks(int seconds) { return Math.max(10, seconds) * 20L; }
    static int quitPresenceTtlSeconds(int graceSeconds) { return Math.max(10, graceSeconds) + 10; }

    private int transferGraceSeconds() {
        return plugin.getConfigManager().getMainConfig().getInt("redis.transfer-grace-seconds", 20);
    }

    private void onMain(Runnable task) {
        if (!plugin.isEnabled()) return;
        Bukkit.getScheduler().runTask(plugin, task);
    }

    private static String safe(Exception error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message.replaceAll("(?i)(password|user)=[^&\\s]+", "$1=***");
    }
}
