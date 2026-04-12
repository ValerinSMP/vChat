package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordBridgeManager {

    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("\"retry_after\"\\s*:\\s*([0-9.]+)");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)(?:https?://|www\\.)\\S+|\\b\\S+\\.(?:com|net|org|gg|io|me|tv|app|dev|xyz|co|us|uk|es|mx|ar|cl|pe|br|fr|de|ru|jp|kr|cn|info)\\b");
    private static final String ALLOWED_PUNCTUATION = ".,!?;:'\"-_()[]/";

    private final VChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ExecutorService webhookExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread t = new Thread(r, "vChat-DiscordWebhook");
            t.setDaemon(true);
            return t;
        }
    });

    private volatile net.dv8tion.jda.api.JDA jda;
    private volatile boolean enabled;
    private volatile String serverId;
    private volatile String guildId;
    private volatile String token;
    private volatile BridgeRoute currentRoute;
    private volatile String inboundFormat;
    private volatile String outboundContentFormat;
    private volatile String outboundUsernameFormat;
    private volatile String outboundAvatarUrl;
    private volatile boolean blockBots;
    private volatile boolean blockWebhooks;
    private volatile int maxDiscordMessageLength;
    private volatile int maxDiscordNameLength;
    private volatile boolean channelTopicEnabled;
    private volatile String channelTopicFormat;
    private volatile int channelTopicUpdateIntervalSeconds;
    private volatile boolean debug;
    private volatile int topicTaskId = -1;
    private volatile String lastAppliedTopic = "";

    private final Set<String> blockedDiscordUsers = ConcurrentHashMap.newKeySet();
    private volatile List<Pattern> blockedWordPatterns = List.of();

    public DiscordBridgeManager(VChat plugin) {
        this.plugin = plugin;
    }

    public void start() {
        reloadInternal(true);
    }

    public void reload() {
        reloadInternal(false);
    }

    public void shutdown() {
        stopTopicUpdater();
        shutdownJda();
        webhookExecutor.shutdownNow();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getServerId() {
        return serverId;
    }

    public String getCurrentChannelId() {
        BridgeRoute route = currentRoute;
        return route != null ? route.channelId() : "";
    }

    public boolean blockDiscordUser(String discordUserId) {
        boolean added = blockedDiscordUsers.add(discordUserId);
        if (added) {
            saveBlockedDiscordUsers();
        }
        return added;
    }

    public boolean unblockDiscordUser(String discordUserId) {
        boolean removed = blockedDiscordUsers.remove(discordUserId);
        if (removed) {
            saveBlockedDiscordUsers();
        }
        return removed;
    }

    public Set<String> getBlockedDiscordUsers() {
        return Set.copyOf(blockedDiscordUsers);
    }

    public void relayMinecraftChat(Player sender, String message) {
        if (!enabled || message == null || message.isBlank()) {
            debug("Skip MC->DS relay: disabled or empty message.");
            return;
        }

        BridgeRoute route = currentRoute;
        if (route == null || route.webhookUrl().isBlank()) {
            debug("Skip MC->DS relay: missing route or webhook-url for server-id '" + serverId + "'.");
            return;
        }

        String safeMessage = truncate(message.trim(), 1800);
        String content = outboundContentFormat
                .replace("%server%", serverId)
                .replace("%player%", sender.getName())
                .replace("%message%", safeMessage);

        String username = outboundUsernameFormat
                .replace("%server%", serverId)
                .replace("%player%", sender.getName());

        webhookExecutor.submit(() -> sendWebhook(route.webhookUrl(), content, username, outboundAvatarUrl));
        debug("Queued MC->DS relay for player '" + sender.getName() + "'.");
    }

    public void sendTestMessageToDiscord(String message) {
        if (!enabled) {
            return;
        }
        BridgeRoute route = currentRoute;
        if (route == null || route.webhookUrl().isBlank()) {
            return;
        }
        String clean = sanitizePlainText(message == null ? "" : message);
        String content = clean.isBlank() ? "[Bridge Test] ok" : "[Bridge Test] " + truncate(clean, 200);
        webhookExecutor.submit(() -> sendWebhook(route.webhookUrl(), content, "[Bridge Test] " + serverId, outboundAvatarUrl));
    }

    private synchronized void reloadInternal(boolean startup) {
        FileConfiguration cfg = plugin.getConfigManager().getBridge();

        stopTopicUpdater();

        enabled = cfg.getBoolean("enabled", false);
        serverId = cfg.getString("server-id", "survival-custom");
        guildId = cfg.getString("guild-id", "");
        token = cfg.getString("bot-token", "");
        if (token == null) {
            token = "";
        }
        if (token.isBlank()) {
            token = System.getenv("VCHAT_DISCORD_BOT_TOKEN");
            if (token == null) {
                token = "";
            }
        }

        inboundFormat = cfg.getString("discord-to-minecraft.format",
                "<dark_gray>[<blue>Discord</blue>]</dark_gray> <gray><user></gray>: <white><message></white>");
        outboundContentFormat = cfg.getString("minecraft-to-discord.content-format", "**%player%**: %message%");
        outboundUsernameFormat = cfg.getString("minecraft-to-discord.username-format", "[%server%] %player%");
        outboundAvatarUrl = sanitizeAvatarUrl(cfg.getString("minecraft-to-discord.avatar-url", ""));

        blockBots = cfg.getBoolean("moderation.block-bots", true);
        blockWebhooks = cfg.getBoolean("moderation.block-webhooks", true);
        maxDiscordMessageLength = cfg.getInt("moderation.max-discord-message-length", 220);
        maxDiscordNameLength = cfg.getInt("moderation.max-discord-name-length", 16);
        channelTopicEnabled = cfg.getBoolean("channel-topic.enabled", false);
        channelTopicFormat = cfg.getString("channel-topic.format",
            "Online: %online%/%max% | Servidor: %server% | Hora: %time%");
        channelTopicUpdateIntervalSeconds = Math.max(15, cfg.getInt("channel-topic.update-interval-seconds", 60));
        debug = cfg.getBoolean("debug", false);

        blockedDiscordUsers.clear();
        blockedDiscordUsers.addAll(cfg.getStringList("moderation.blocked-discord-user-ids"));

        blockedWordPatterns = compileBlockedWordPatterns(cfg);
        currentRoute = loadRoute(cfg, serverId);

        if (!enabled) {
            shutdownJda();
            return;
        }

        if (currentRoute == null) {
            plugin.getLogger().warning("[Bridge] Route not found for server-id '" + serverId + "'. Bridge disabled.");
            enabled = false;
            shutdownJda();
            return;
        }
        debug("Loaded route for server-id '" + serverId + "' channel '" + currentRoute.channelId() + "'.");

        if (token.isBlank()) {
            plugin.getLogger().warning("[Bridge] Missing bot-token in bridge.yml. Discord->Minecraft will be disabled.");
            shutdownJda();
            return;
        }

        shutdownJda();
        try {
            jda = JDABuilder.createLight(token,
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .addEventListeners(new DiscordInboundListener())
                    .build();

            startTopicUpdater();

            if (startup) {
                plugin.getLogger().info("[Bridge] Discord bridge started for server-id '" + serverId + "'.");
            } else {
                plugin.getLogger().info("[Bridge] Discord bridge reloaded for server-id '" + serverId + "'.");
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("[Bridge] Failed to initialize Discord bridge: " + ex.getMessage());
            enabled = false;
        }
    }

    private void shutdownJda() {
        net.dv8tion.jda.api.JDA ref = jda;
        jda = null;
        lastAppliedTopic = "";
        if (ref != null) {
            try {
                ref.shutdownNow();
            } catch (Exception ignored) {
            }
        }
    }

    private BridgeRoute loadRoute(FileConfiguration cfg, String id) {
        ConfigurationSection sec = cfg.getConfigurationSection("routes." + id);
        if (sec == null) {
            return null;
        }
        String channelId = sec.getString("channel-id", "");
        String webhookUrl = sec.getString("webhook-url", "");
        return new BridgeRoute(channelId, webhookUrl);
    }

    private List<Pattern> compileBlockedWordPatterns(FileConfiguration bridgeCfg) {
        List<String> words = new ArrayList<>();
        if (bridgeCfg.getBoolean("moderation.use-filters-profanity-words", true)) {
            words.addAll(plugin.getConfigManager().getFilters().getStringList("profanity.words"));
        }
        words.addAll(bridgeCfg.getStringList("moderation.extra-blocked-words"));

        List<Pattern> patterns = new ArrayList<>();
        for (String word : words) {
            String trimmed = word.trim();
            if (!trimmed.isEmpty()) {
                patterns.add(createRobustPattern(trimmed));
            }
        }
        return List.copyOf(patterns);
    }

    private boolean containsBlockedWord(String message) {
        for (Pattern p : blockedWordPatterns) {
            if (p.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    private Pattern createRobustPattern(String word) {
        StringBuilder robust = new StringBuilder("(?i)");
        char[] chars = word.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            robust.append(getRegexForChar(chars[i])).append("+");
            if (i < chars.length - 1) {
                robust.append("[^a-zA-Z0-9]*");
            }
        }
        return Pattern.compile(robust.toString());
    }

    private String getRegexForChar(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'a' -> "[a4@]";
            case 'b' -> "[b8]";
            case 'c' -> "[c(\\[]";
            case 'e' -> "[e3]";
            case 'g' -> "[g69]";
            case 'h' -> "[h#]";
            case 'i' -> "[i1l!]";
            case 'o' -> "[o0]";
            case 's' -> "[s5$]";
            case 't' -> "[t7+]";
            case 'z' -> "[z2]";
            default -> Pattern.quote(String.valueOf(c));
        };
    }

    private void saveBlockedDiscordUsers() {
        FileConfiguration cfg = plugin.getConfigManager().getBridge();
        cfg.set("moderation.blocked-discord-user-ids", new ArrayList<>(blockedDiscordUsers));
        plugin.getConfigManager().saveConfig("bridge.yml");
    }

    private void sendWebhook(String webhookUrl, String content, String username, String avatarUrl) {
        try {
            HttpRequest request = buildWebhookRequest(webhookUrl, toWebhookPayload(content, username, avatarUrl, true));
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status == 400) {
                debug("Webhook 400 response body: " + response.body());
                // Fallback ultra compatible: only content
                HttpRequest fallbackRequest = buildWebhookRequest(webhookUrl, toWebhookPayload(content, username, avatarUrl, false));
                HttpResponse<String> fallbackResponse = httpClient.send(fallbackRequest,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int fallbackStatus = fallbackResponse.statusCode();
                if (fallbackStatus / 100 == 2) {
                    debug("Webhook fallback (content-only) successful.");
                    return;
                }
                plugin.getLogger().warning("[Bridge] Webhook fallback failed with status " + fallbackStatus);
                debug("Webhook fallback body: " + fallbackResponse.body());
                return;
            }

            if (status == 429) {
                long sleepMs = parseRetryAfterMillis(response.body());
                if (sleepMs > 0L) {
                    Thread.sleep(Math.min(sleepMs, 5000L));
                    HttpResponse<String> retryResponse = httpClient.send(request,
                            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (retryResponse.statusCode() / 100 != 2) {
                        plugin.getLogger().warning("[Bridge] Webhook retry failed with status " + retryResponse.statusCode());
                        debug("Webhook retry body: " + retryResponse.body());
                    }
                }
                return;
            }

            if (status / 100 != 2) {
                plugin.getLogger().warning("[Bridge] Webhook send failed with status " + status);
                debug("Webhook error body: " + response.body());
            } else {
                debug("Webhook send successful.");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("[Bridge] Webhook send error: " + ex.getMessage());
        }
    }

    private HttpRequest buildWebhookRequest(String webhookUrl, String payload) {
        return HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl.trim()))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
    }

    private long parseRetryAfterMillis(String body) {
        if (body == null || body.isBlank()) {
            return 0L;
        }
        Matcher m = RETRY_AFTER_PATTERN.matcher(body);
        if (!m.find()) {
            return 0L;
        }
        try {
            double seconds = Double.parseDouble(m.group(1));
            return (long) (seconds * 1000.0);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String toWebhookPayload(String content, String username, String avatarUrl, boolean includeMeta) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{");
        sb.append("\"content\":\"").append(escapeJson(truncate(content, 1900))).append("\"");
        if (includeMeta) {
            sb.append(",\"username\":\"").append(escapeJson(truncate(username, 80))).append("\"");
            sb.append(",\"allowed_mentions\":{\"parse\":[]}");
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                sb.append(",\"avatar_url\":\"").append(escapeJson(avatarUrl)).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    private String truncate(String input, int limit) {
        if (input.length() <= limit) {
            return input;
        }
        return input.substring(0, limit);
    }

    private final class DiscordInboundListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event) {
            if (!enabled) {
                return;
            }
            if (!event.isFromGuild()) {
                debug("Skip DS->MC: not from guild.");
                return;
            }
            if (!event.getGuild().getId().equals(guildId)) {
                debug("Skip DS->MC: guild mismatch. Got " + event.getGuild().getId() + " expected " + guildId);
                return;
            }

            BridgeRoute route = currentRoute;
            if (route == null || !event.getChannel().getId().equals(route.channelId())) {
                if (route != null) {
                    debug("Skip DS->MC: channel mismatch. Got " + event.getChannel().getId() + " expected " + route.channelId());
                }
                return;
            }
            if (blockWebhooks && event.isWebhookMessage()) {
                debug("Skip DS->MC: webhook message blocked.");
                return;
            }
            if (blockBots && event.getAuthor().isBot()) {
                debug("Skip DS->MC: bot message blocked.");
                return;
            }

            String authorId = event.getAuthor().getId();
            if (blockedDiscordUsers.contains(authorId)) {
                debug("Skip DS->MC: user blocked " + authorId);
                return;
            }

            Message msg = event.getMessage();
            if (!msg.getAttachments().isEmpty() || !msg.getEmbeds().isEmpty() || !msg.getStickers().isEmpty()) {
                debug("Skip DS->MC: attachments/embeds/stickers are not allowed.");
                return;
            }
            if (URL_PATTERN.matcher(msg.getContentRaw()).find()) {
                debug("Skip DS->MC: url/link detected.");
                return;
            }

            String content = sanitizePlainText(msg.getContentDisplay());
            if (content.isBlank()) {
                debug("Skip DS->MC: content became empty after sanitization.");
                return;
            }
            content = truncate(content, maxDiscordMessageLength);
            if (containsBlockedWord(content)) {
                debug("Skip DS->MC: blocked word detected.");
                return;
            }

            String rawUsername = msg.getMember() != null ? msg.getMember().getEffectiveName() : event.getAuthor().getName();
            String username = sanitizeUsername(rawUsername);
            if (username.isBlank()) {
                username = "DiscordUser";
            }
            String channelName = event.getChannel().getName();

            Component rendered = miniMessage.deserialize(inboundFormat,
                    Placeholder.unparsed("user", username),
                    Placeholder.unparsed("message", content),
                    Placeholder.unparsed("channel", channelName),
                    Placeholder.unparsed("server", serverId));

            Bukkit.getScheduler().runTask(plugin, () -> {
                int delivered = 0;
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (plugin.getAdminManager().isPersonalChatMuted(online)) {
                        continue;
                    }
                    online.sendMessage(rendered);
                    delivered++;
                }
                debug("Delivered DS->MC message to " + delivered + " players.");
            });
        }
    }

    private record BridgeRoute(String channelId, String webhookUrl) {
    }

    private String sanitizeUsername(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                out.append(c);
            } else if (Character.isWhitespace(c) && !out.isEmpty() && out.charAt(out.length() - 1) != ' ') {
                out.append(' ');
            }
        }
        return truncate(out.toString().trim(), Math.max(1, maxDiscordNameLength));
    }

    private String sanitizePlainText(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                out.append(c);
                lastWasSpace = false;
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (!lastWasSpace && !out.isEmpty()) {
                    out.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }
            if (ALLOWED_PUNCTUATION.indexOf(c) >= 0) {
                out.append(c);
                lastWasSpace = false;
            }
        }
        return out.toString().trim();
    }

    private String sanitizeAvatarUrl(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
            plugin.getLogger().warning("[Bridge] Ignoring invalid minecraft-to-discord.avatar-url (must start with http/https).");
            return "";
        }
        return trimmed;
    }

    private void startTopicUpdater() {
        if (!enabled || !channelTopicEnabled) {
            return;
        }

        if (topicTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(topicTaskId);
            topicTaskId = -1;
        }

        long periodTicks = Math.max(20L, channelTopicUpdateIntervalSeconds * 20L);
        topicTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin,
                this::updateChannelTopicIfNeeded,
                40L,
                periodTicks);
        debug("Channel topic updater started. Interval=" + channelTopicUpdateIntervalSeconds + "s");
    }

    private void stopTopicUpdater() {
        if (topicTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(topicTaskId);
            topicTaskId = -1;
            debug("Channel topic updater stopped.");
        }
    }

    private void updateChannelTopicIfNeeded() {
        if (!enabled || !channelTopicEnabled) {
            return;
        }

        net.dv8tion.jda.api.JDA currentJda = jda;
        BridgeRoute route = currentRoute;
        if (currentJda == null || route == null || route.channelId().isBlank()) {
            return;
        }

        TextChannel channel = currentJda.getTextChannelById(route.channelId());
        if (channel == null) {
            debug("Channel topic updater: target channel not found.");
            return;
        }

        String topic = renderTopicTemplate();
        if (topic.isBlank()) {
            return;
        }

        if (topic.equals(lastAppliedTopic)) {
            return;
        }

        RestAction<Void> action = channel.getManager().setTopic(topic);
        action.queue(
                success -> {
                    lastAppliedTopic = topic;
                    debug("Channel topic updated.");
                },
                failure -> plugin.getLogger().warning("[Bridge] Could not update channel topic: " + failure.getMessage()));
    }

    private String renderTopicTemplate() {
        String format = channelTopicFormat == null ? "" : channelTopicFormat;
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        java.time.LocalTime now = java.time.LocalTime.now();

        String topic = format
                .replace("%server%", serverId)
                .replace("%online%", String.valueOf(online))
                .replace("%max%", String.valueOf(max))
                .replace("%time%", now.withNano(0).toString());

        if (topic.length() > 1024) {
            topic = topic.substring(0, 1024);
        }
        return topic.trim();
    }

    private void debug(String message) {
        if (debug) {
            plugin.getLogger().info("[Bridge][Debug] " + message);
        }
    }
}
