package me.marti.vchat.redis;

import com.google.gson.Gson;
import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

import java.util.Locale;
import java.util.UUID;

/**
 * Multiserver via Redis (estilo William278: pool Jedis + hilo suscriptor dedicado
 * + reconexión con backoff fijo). Solo soportado en Paper — en Arclight/Spigot
 * ni siquiera se toca una clase de Jedis (isPaper() se chequea antes de cualquier uso).
 */
public class RedisManager {

    private static final int RECONNECT_DELAY_MS = 8000;

    private final VChat plugin;
    private final Gson gson = new Gson();
    private JedisPool pool;
    private Thread subscriberThread;
    private JedisPubSub subscriber;
    private volatile boolean enabled;
    private volatile boolean running;
    private String serverId;
    private String clusterId;
    private String channel;

    public RedisManager(VChat plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        FileConfiguration cfg = plugin.getConfigManager().getMainConfig();
        if (!cfg.getBoolean("redis.enabled", false)) return;

        if (!PlatformUtil.isPaper()) {
            plugin.getLogger().warning("Redis multiserver solo soportado en Paper. Desactivado en este servidor.");
            return;
        }

        this.serverId = cfg.getString("redis.server-id", "server1");
        this.clusterId = cfg.getString("redis.cluster-id", "valerin");
        this.channel = channelName(clusterId);

        String host = cfg.getString("redis.host", "localhost");
        int port = cfg.getInt("redis.port", 6379);
        String user = cfg.getString("redis.user", "");
        String password = cfg.getString("redis.password", "");
        int database = cfg.getInt("redis.database", 0);
        boolean ssl = cfg.getBoolean("redis.use-ssl", false);

        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(8);
            poolConfig.setTestOnBorrow(true);

            DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                    .ssl(ssl).database(database).timeoutMillis(3000)
                    .user(user.isEmpty() ? null : user)
                    .password(password.isEmpty() ? null : password)
                    .build();

            this.pool = new JedisPool(poolConfig, new HostAndPort(host, port), clientConfig);
            try (Jedis jedis = pool.getResource()) {
                jedis.ping();
            }

            this.running = true;
            this.subscriber = new Listener();
            this.subscriberThread = new Thread(this::subscribeLoop, "vchat:redis-subscriber");
            this.subscriberThread.setDaemon(true);
            this.subscriberThread.start();

            this.enabled = true;
            plugin.getLogger().info("Redis conectado (" + host + ":" + port + ", cluster=" + clusterId + ", server=" + serverId + ")");
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo conectar a Redis, multiserver desactivado: " + e.getMessage());
            this.enabled = false;
            if (pool != null) pool.close();
        }
    }

    public void disable() {
        running = false;
        enabled = false;
        if (subscriber != null) {
            try { subscriber.unsubscribe(); } catch (Exception ignored) {}
        }
        if (subscriberThread != null) subscriberThread.interrupt();
        if (pool != null) pool.close();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServerId() {
        return serverId;
    }

    private void subscribeLoop() {
        boolean reconnecting = false;
        while (running && pool != null && !pool.isClosed()) {
            try (Jedis jedis = pool.getResource()) {
                if (reconnecting) plugin.getLogger().info("Conexión Redis restablecida.");
                jedis.subscribe(subscriber, channel);
            } catch (Exception e) {
                if (!running) return;
                plugin.getLogger().warning("Conexión Redis perdida, reintentando en "
                        + (RECONNECT_DELAY_MS / 1000) + "s...");
                reconnecting = true;
                try {
                    Thread.sleep(RECONNECT_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private class Listener extends JedisPubSub {
        @Override
        public void onMessage(String ch, String message) {
            RedisEvent event;
            try {
                event = gson.fromJson(message, RedisEvent.class);
            } catch (Exception e) {
                plugin.getLogger().warning("Evento Redis inválido: " + e.getMessage());
                return;
            }
            if (isLoopback(event, serverId)) return;
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getRedisEventHandler().handle(event));
        }
    }

    public void publish(RedisEvent event) {
        if (!enabled) return;
        event.sourceServer = serverId;
        event.timestamp = System.currentTimeMillis();
        String json = gson.toJson(event);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish(channel, json);
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo publicar evento a Redis: " + e.getMessage());
            }
        });
    }

    private String presenceKey() {
        return presenceKey(clusterId);
    }

    private String msgToggleKey() {
        return msgToggleKey(clusterId);
    }

    private String ignoreKey(UUID uuid) {
        return ignoreKey(clusterId, uuid);
    }

    private String chatMuteKey() {
        return "vchat:" + clusterId + ":chatmute";
    }

    private String deathMuteKey() {
        return "vchat:" + clusterId + ":deathmute";
    }

    private String everJoinedKey() {
        return "vchat:" + clusterId + ":everjoined";
    }

    private String joinCounterKey() {
        return "vchat:" + clusterId + ":joincounter";
    }

    // ── Naming/parsing puros — sin estado, testeables sin un Redis real ────────────

    static String channelName(String clusterId) {
        return "vchat:" + clusterId + ":events";
    }

    static String presenceKey(String clusterId) {
        return "vchat:" + clusterId + ":presence";
    }

    static String msgToggleKey(String clusterId) {
        return "vchat:" + clusterId + ":msgtoggle";
    }

    static String ignoreKey(String clusterId, UUID uuid) {
        return "vchat:" + clusterId + ":ignore:" + uuid;
    }

    static String presenceValue(String serverId, UUID uuid) {
        return serverId + "|" + uuid;
    }

    /** {serverId, uuid} desde un valor "server|uuid", o null si está corrupto. */
    static String[] parsePresenceValue(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split("\\|", 2);
        return parts.length == 2 ? parts : null;
    }

    /**
     * true si el evento debe descartarse: es un eco propio, o le falta 'type'/'sourceServer'.
     * Gson no lanza al deserializar un enum desconocido — deja el campo en null en vez de
     * tirar JsonSyntaxException — así que un 'type' corrupto debe filtrarse acá, no asumir
     * que fromJson ya lo habría rechazado.
     */
    static boolean isLoopback(RedisEvent event, String localServerId) {
        return event == null || event.type == null || event.sourceServer == null
                || event.sourceServer.equals(localServerId);
    }

    public void setPlayerOnline(UUID uuid, String name) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(presenceKey(), name.toLowerCase(Locale.ROOT), presenceValue(serverId, uuid));
            plugin.debugLog("Redis: presencia registrada para " + name + " en " + serverId);
        } catch (Exception e) {
            plugin.debugLog("Redis: no se pudo registrar presencia de " + name + ": " + e.getMessage());
        }
    }

    public void setPlayerOffline(String name) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hdel(presenceKey(), name.toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
        }
    }

    /**
     * Borra la presencia solo si TODAVÍA apunta a expectedServer. Usado con un delay
     * desde QuitListener: si el jugador saltó a otro server del cluster (hub -> survival),
     * ese server ya sobrescribió la entrada con setPlayerOnline antes de que esto corra,
     * así que no la toca — evita que un quit "de tránsito" borre la presencia recién puesta
     * por el server destino.
     */
    public void clearPlayerIfServerMatches(String name, String expectedServer) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            String key = name.toLowerCase(Locale.ROOT);
            String[] current = parsePresenceValue(jedis.hget(presenceKey(), key));
            if (current != null && current[0].equals(expectedServer)) {
                jedis.hdel(presenceKey(), key);
            }
        } catch (Exception ignored) {
        }
    }

    /** {serverId, uuid} del jugador remoto, o null si no está online en ningún server del cluster. */
    public String[] findRemotePlayer(String name) {
        if (!enabled) return null;
        try (Jedis jedis = pool.getResource()) {
            String[] result = parsePresenceValue(jedis.hget(presenceKey(), name.toLowerCase(Locale.ROOT)));
            plugin.debugLog("Redis: búsqueda remota de '" + name + "' -> "
                    + (result == null ? "no encontrado" : "server=" + result[0]));
            return result;
        } catch (Exception e) {
            plugin.debugLog("Redis: fallo buscando '" + name + "' remoto: " + e.getMessage());
            return null;
        }
    }

    public void setMsgToggle(UUID uuid, boolean value) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(msgToggleKey(), uuid.toString(), value ? "1" : "0");
        } catch (Exception ignored) {
        }
    }

    /** null si no hay dato remoto (asumir default true). */
    public Boolean getRemoteMsgToggle(UUID uuid) {
        if (!enabled) return null;
        try (Jedis jedis = pool.getResource()) {
            String v = jedis.hget(msgToggleKey(), uuid.toString());
            return v == null ? null : v.equals("1");
        } catch (Exception e) {
            return null;
        }
    }

    public void setIgnoreList(UUID uuid, java.util.Set<UUID> ignored) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            String key = ignoreKey(uuid);
            jedis.del(key);
            if (!ignored.isEmpty()) {
                jedis.sadd(key, ignored.stream().map(UUID::toString).toArray(String[]::new));
            }
        } catch (Exception ignored2) {
        }
    }

    public boolean isIgnoredRemote(UUID target, UUID by) {
        if (!enabled) return false;
        try (Jedis jedis = pool.getResource()) {
            return jedis.sismember(ignoreKey(target), by.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /** Set completo de ignorados de un jugador, tal como lo dejó el último server donde tocó /ignore. */
    public java.util.Set<UUID> getIgnoreList(UUID uuid) {
        if (!enabled) return java.util.Set.of();
        try (Jedis jedis = pool.getResource()) {
            java.util.Set<String> raw = jedis.smembers(ignoreKey(uuid));
            java.util.Set<UUID> result = new java.util.HashSet<>();
            for (String s : raw) {
                try { result.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
            }
            return result;
        } catch (Exception e) {
            return java.util.Set.of();
        }
    }

    public void setChatMuteToggle(UUID uuid, boolean value) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(chatMuteKey(), uuid.toString(), value ? "1" : "0");
        } catch (Exception ignored) {
        }
    }

    /** null si no hay dato remoto (caller debe quedarse con el valor local/PDC). */
    public Boolean getRemoteChatMuteToggle(UUID uuid) {
        if (!enabled) return null;
        try (Jedis jedis = pool.getResource()) {
            String v = jedis.hget(chatMuteKey(), uuid.toString());
            return v == null ? null : v.equals("1");
        } catch (Exception e) {
            return null;
        }
    }

    public void setDeathMuteToggle(UUID uuid, boolean value) {
        if (!enabled) return;
        try (Jedis jedis = pool.getResource()) {
            jedis.hset(deathMuteKey(), uuid.toString(), value ? "1" : "0");
        } catch (Exception ignored) {
        }
    }

    public Boolean getRemoteDeathMuteToggle(UUID uuid) {
        if (!enabled) return null;
        try (Jedis jedis = pool.getResource()) {
            String v = jedis.hget(deathMuteKey(), uuid.toString());
            return v == null ? null : v.equals("1");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * true solo la primera vez que este uuid se ve en TODA la red (SADD devuelve 1).
     * player.hasPlayedBefore() de Bukkit es local al server, así que un jugador que ya
     * jugó en server1 pero nunca en server2 daba isFirstJoin=true de nuevo ahí — este
     * chequeo es el que corrige eso a nivel de cluster. Si Redis está deshabilitado no
     * hay forma de saberlo a nivel red, se devuelve true (comportamiento previo: decide
     * el hasPlayedBefore local nomás).
     */
    public boolean markFirstNetworkJoin(UUID uuid) {
        if (!enabled) return true;
        try (Jedis jedis = pool.getResource()) {
            return jedis.sadd(everJoinedKey(), uuid.toString()) == 1;
        } catch (Exception e) {
            return true;
        }
    }

    /** Número de jugador correlativo a nivel de TODA la red (antes era un contador local por server). */
    public long nextNetworkPlayerNumber() {
        if (!enabled) return -1;
        try (Jedis jedis = pool.getResource()) {
            return jedis.incr(joinCounterKey());
        } catch (Exception e) {
            return -1;
        }
    }

    private String topicLeaderKey(String channelId) {
        return "vchat:" + clusterId + ":topic-leader:" + channelId;
    }

    /**
     * Lease-based leader election: cuando dos servers apuntan al mismo canal de Discord
     * para el tópico, solo el líder debe llamar a la API (el rate limit de PATCH de
     * canal es muy estricto y se agota al doble de rápido con dos servers escribiendo).
     * Si Redis está deshabilitado, siempre devuelve true (comportamiento sin coordinar,
     * igual que antes de esto existir).
     */
    public boolean claimTopicLeadership(String channelId, long ttlMillis) {
        if (!enabled) return true;
        try (Jedis jedis = pool.getResource()) {
            String key = topicLeaderKey(channelId);
            String current = jedis.get(key);
            if (serverId.equals(current)) {
                jedis.pexpire(key, ttlMillis);
                return true;
            }
            String result = jedis.set(key, serverId, SetParams.setParams().nx().px(ttlMillis));
            return "OK".equals(result);
        } catch (Exception e) {
            // Fail-open: mejor que dos servers escriban el tópico de más a que ninguno lo actualice.
            return true;
        }
    }

    /** Cantidad total de jugadores online en todo el cluster, o -1 si Redis no está activo. */
    public long getNetworkOnlineCount() {
        if (!enabled) return -1;
        try (Jedis jedis = pool.getResource()) {
            return jedis.hlen(presenceKey());
        } catch (Exception e) {
            return -1;
        }
    }
}
