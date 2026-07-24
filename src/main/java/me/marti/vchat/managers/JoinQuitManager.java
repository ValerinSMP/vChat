package me.marti.vchat.managers;

import me.clip.placeholderapi.PlaceholderAPI;
import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class JoinQuitManager {

    private final VChat plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private File dataFile;
    private YamlConfiguration dataConfig;

    private static final String FIRST_JOIN_KEY = "player-count";

    public JoinQuitManager(VChat plugin) {
        this.plugin = plugin;
        loadDataFile();
    }

    private void loadDataFile() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    /** Contador de red si Redis está activo (número correcto sumando ambos servers), local si no. */
    private int getPlayerNumber() {
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            long n = plugin.getRedisManager().nextNetworkPlayerNumber();
            if (n > 0) return (int) n;
        }
        return incrementPlayerCount();
    }

    private synchronized int incrementPlayerCount() {
        int count = dataConfig.getInt(FIRST_JOIN_KEY, 0) + 1;
        dataConfig.set(FIRST_JOIN_KEY, count);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
        return count;
    }

    public void handleJoin(Player player) {
        FileConfiguration cfg = plugin.getConfigManager().getMessages();
        boolean isFirstJoin = !player.hasPlayedBefore();
        // hasPlayedBefore() es local a ESTE server. Si ya jugó en server1 pero nunca en
        // server2, acá volvía a dar true. markFirstNetworkJoin chequea/agrega contra Redis
        // (SADD a un set de la red entera) y solo es true la primera vez en TODO el cluster.
        // SIEMPRE se llama (aunque hasPlayedBefore() ya sea true acá) para hacer backfill:
        // un jugador viejo que jugó desde antes de este fix nunca quedó registrado en el
        // set, así que hay que agregarlo la primera vez que se lo vea en CUALQUIER server,
        // sin que eso dispare el mensaje de "primera vez" si localmente ya se sabía que no lo era.
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            boolean firstInNetwork = plugin.getRedisManager().markFirstNetworkJoin(player.getUniqueId());
            if (isFirstJoin) {
                isFirstJoin = firstInNetwork;
            }
        }

        String format;
        String soundPath;

        if (isFirstJoin) {
            int playerNumber = getPlayerNumber();
            format = cfg.getString("join.first-join",
                    "<yellow>⭐ <gold><lp_prefix><player_name></gold> <yellow>sᴇ ᴜɴᴇ ᴘᴏʀ ᴘʀɪᴍᴇʀᴀ ᴠᴇᴢ ⭐ <gold>(#<player_number>)</gold>");
            format = format.replace("<player_number>", String.valueOf(playerNumber));
            soundPath = cfg.getString("join.first-join-sound", "");
        } else {
            format = getGroupFormat(player, cfg);
            soundPath = getGroupSoundPath(player, cfg);
        }

        // format vacío ("" en el yml) = sin anuncio de join para este grupo, a propósito.
        if (format != null && !format.isBlank()) {
            int online = Bukkit.getOnlinePlayers().size();
            format = format.replace("<online>", String.valueOf(online));
            broadcast(buildMessage(player, format));
        }
        playJoinSound(player, soundPath, cfg);
    }

    public void handleQuit(Player player) {
        // Quit messages suppressed — set to null in the event (done in QuitListener)
    }

    private String getGroupFormat(Player player, FileConfiguration cfg) {
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String groupFormat = cfg.getString("join.group-formats." + user.getPrimaryGroup(), null);
            if (groupFormat != null && !groupFormat.isBlank()) return groupFormat;
        }
        return cfg.getString("join.format",
                "<gold>🌊 <lp_prefix><player_name></gold> <yellow>sᴇ ʜᴀ ᴜɴɪᴅᴏ <gold>🌊 <dark_gray>(<online>)");
    }

    private String getGroupSoundPath(Player player, FileConfiguration cfg) {
        // Check group-specific sounds first (higher weight first via LuckPerms)
        User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            String primaryGroup = user.getPrimaryGroup();
            String groupSound = cfg.getString("join.group-sounds." + primaryGroup, null);
            if (groupSound != null && !groupSound.isBlank()) {
                return "join.group-sounds." + primaryGroup;
            }
        }
        return "join.sound";
    }

    private void playJoinSound(Player player, String soundConfigPath, FileConfiguration cfg) {
        String raw;
        if (soundConfigPath.startsWith("join.group-sounds.")) {
            String group = soundConfigPath.substring("join.group-sounds.".length());
            raw = cfg.getString("join.group-sounds." + group, "");
        } else {
            raw = cfg.getString(soundConfigPath, "");
        }
        if (raw == null || raw.isBlank()) return;

        // Format: "SOUND_NAME [volume] [pitch]"
        String[] parts = raw.trim().split("\\s+");
        org.bukkit.Sound sound = PlatformUtil.resolveSound(parts[0]);
        if (sound == null) return;
        float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
        float pitch  = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.playSound(online.getLocation(), sound, volume, pitch);
        }
    }

    private Component buildMessage(Player player, String format) {
        String prefix = "";
        net.luckperms.api.cacheddata.CachedMetaData meta =
                plugin.getLuckPerms().getPlayerAdapter(Player.class).getMetaData(player);
        if (meta.getPrefix() != null) prefix = meta.getPrefix();

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            prefix = PlaceholderAPI.setPlaceholders(player, prefix);
            format = PlaceholderAPI.setPlaceholders(player, format);
        }

        // Convert prefix from legacy to MiniMessage string and substitute inline,
        // so MiniMessage parses everything as one string and preserves style context.
        String prefixMM = miniMessage.serialize(legacyAmp.deserialize(prefix));
        format = translateLegacyHex(format);
        format = translateLegacy(format);
        format = format
                .replace("<lp_prefix>", prefixMM)
                .replace("<player_name>", net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                        .escapeTags(player.getName()));

        return miniMessage.deserialize(format);
    }

    private void broadcast(Component message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            PlatformUtil.sendMessage(online, message);
        }
        PlatformUtil.sendMessage(Bukkit.getConsoleSender(), message);
    }

    private String translateLegacyHex(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, "<#" + m.group(1) + ">");
        m.appendTail(sb);
        return sb.toString();
    }

    private String translateLegacy(String s) {
        return s.replace("&0","<black>").replace("&1","<dark_blue>").replace("&2","<dark_green>")
                .replace("&3","<dark_aqua>").replace("&4","<dark_red>").replace("&5","<dark_purple>")
                .replace("&6","<gold>").replace("&7","<gray>").replace("&8","<dark_gray>")
                .replace("&9","<blue>").replace("&a","<green>").replace("&b","<aqua>")
                .replace("&c","<red>").replace("&d","<light_purple>").replace("&e","<yellow>")
                .replace("&f","<white>").replace("&l","<bold>").replace("&m","<strikethrough>")
                .replace("&n","<underlined>").replace("&o","<italic>").replace("&r","<reset>")
                .replace("§r","<reset>").replace("§l","<bold>");
    }
}
