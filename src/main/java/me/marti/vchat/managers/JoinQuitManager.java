package me.marti.vchat.managers;

import me.clip.placeholderapi.PlaceholderAPI;
import me.marti.vchat.VChat;
import me.marti.vchat.redis.RedisEvent;
import me.marti.vchat.redis.RedisEventType;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class JoinQuitManager {
    private final VChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.builder().character('&').hexColors().build();

    public JoinQuitManager(VChat plugin) {
        this.plugin = plugin;
    }

    public void handleJoin(Player player) {
        boolean locallyNew = !player.hasPlayedBefore();
        String playerName = player.getName();
        plugin.getStorageManager().registerPlayer(player.getUniqueId(), playerName).thenAccept(registration -> {
            if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
                plugin.getRedisManager().networkOnlineCount(count -> announceJoin(player,
                        locallyNew && registration.firstNetworkJoin(), registration.playerNumber(),
                        count < 0 ? Bukkit.getOnlinePlayers().size() : count));
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> announceJoin(player,
                        locallyNew && registration.firstNetworkJoin(), registration.playerNumber(), Bukkit.getOnlinePlayers().size()));
            }
        }).exceptionally(error -> {
            plugin.getLogger().warning("Join registration failed for " + playerName + ".");
            return null;
        });
    }

    private void announceJoin(Player player, boolean firstJoin, long playerNumber, long online) {
        if (!player.isOnline()) return;
        FileConfiguration config = plugin.getConfigManager().getMessages();
        String format;
        String soundPath;
        if (firstJoin) {
            format = config.getString("join.first-join", "<yellow>⭐ <gold><lp_prefix><player_name></gold> <yellow>se une por primera vez ⭐ <gold>(#<player_number>)</gold>");
            format = format.replace("<player_number>", String.valueOf(playerNumber));
            soundPath = "join.first-join-sound";
        } else {
            format = groupFormat(player, config);
            soundPath = groupSoundPath(player, config);
        }
        if (format != null && !format.isBlank()) {
            Component component = buildMessage(player, format.replace("<online>", String.valueOf(online)));
            broadcast(component);
            publish(RedisEventType.JOIN, player.getName(), component);
        }
        playJoinSound(soundPath, config);
    }

    public void handleNetworkQuit(String playerName) {
        String format = plugin.getConfigManager().getMessages().getString("quit.format", "");
        if (format == null || format.isBlank()) return;
        Component component = miniMessage.deserialize(format, Placeholder.unparsed("player_name", playerName));
        broadcast(component);
        publish(RedisEventType.QUIT, playerName, component);
    }

    private void publish(RedisEventType type, String playerName, Component component) {
        if (plugin.getRedisManager() == null) return;
        plugin.getRedisManager().publish(new RedisEvent(type).put("playerName", playerName)
                .put("component", GsonComponentSerializer.gson().serialize(component)));
    }

    private String groupFormat(Player player, FileConfiguration config) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String value = config.getString("join.group-formats." + user.getPrimaryGroup());
            if (value != null && !value.isBlank()) return value;
        }
        return config.getString("join.format", "<gold><lp_prefix><player_name></gold> <yellow>se ha unido <dark_gray>(<online>)");
    }

    private String groupSoundPath(Player player, FileConfiguration config) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String path = "join.group-sounds." + user.getPrimaryGroup();
            if (!config.getString(path, "").isBlank()) return path;
        }
        return "join.sound";
    }

    private Component buildMessage(Player player, String format) {
        String prefix = "";
        var meta = plugin.getLuckPerms().getPlayerAdapter(Player.class).getMetaData(player);
        if (meta.getPrefix() != null) prefix = meta.getPrefix();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            prefix = PlaceholderAPI.setPlaceholders(player, prefix);
            format = PlaceholderAPI.setPlaceholders(player, format);
        }
        return miniMessage.deserialize(translateLegacy(format),
                Placeholder.component("lp_prefix", legacyAmp.deserialize(prefix)),
                Placeholder.unparsed("player_name", player.getName()));
    }

    private void playJoinSound(String path, FileConfiguration config) {
        String raw = config.getString(path, "");
        if (raw == null || raw.isBlank()) return;
        String[] parts = raw.trim().split("\\s+");
        org.bukkit.Sound sound = PlatformUtil.resolveSound(parts[0]);
        if (sound == null) return;
        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
        float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
        for (Player online : Bukkit.getOnlinePlayers()) online.playSound(online.getLocation(), sound, volume, pitch);
    }

    private void broadcast(Component message) {
        for (Player online : Bukkit.getOnlinePlayers()) PlatformUtil.sendMessage(online, message);
        PlatformUtil.sendMessage(Bukkit.getConsoleSender(), message);
    }

    private static String translateLegacy(String value) {
        return value.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>")
                .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                .replace("&f", "<white>").replace("&l", "<bold>").replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>").replace("&o", "<italic>").replace("&r", "<reset>");
    }
}
