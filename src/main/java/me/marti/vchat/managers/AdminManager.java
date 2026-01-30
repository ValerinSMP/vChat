package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class AdminManager {

    private final VChat plugin;
    private final org.bukkit.NamespacedKey notifyKey;
    private final Map<UUID, Boolean> notifyCache = new HashMap<>();
    private final Map<UUID, Integer> violationCounts = new HashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand(); // Restore
                                                                                                            // serializer

    public AdminManager(VChat plugin) {
        this.plugin = plugin;
        this.notifyKey = new org.bukkit.NamespacedKey(plugin, "notify_enabled");
    }

    public boolean isNotifyEnabled(Player player) {
        return notifyCache.getOrDefault(player.getUniqueId(), false);
    }

    public void loadData(Player player) {
        // If key exists, load it.
        // If key does NOT exist -> Check permission. If has 'vchat.notify.auto' ->
        // true, else false.
        boolean value;
        if (player.getPersistentDataContainer().has(notifyKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            value = player.getPersistentDataContainer().get(notifyKey,
                    org.bukkit.persistence.PersistentDataType.BYTE) == 1;
        } else {
            value = player.hasPermission("vchat.notify.auto");
        }
        notifyCache.put(player.getUniqueId(), value);
    }

    public void unloadData(Player player) {
        notifyCache.remove(player.getUniqueId());
    }

    public boolean toggleNotifications(Player player) {
        boolean current = isNotifyEnabled(player);
        boolean newState = !current;

        // Update Cache
        notifyCache.put(player.getUniqueId(), newState);

        // Update PDC
        player.getPersistentDataContainer().set(notifyKey, org.bukkit.persistence.PersistentDataType.BYTE,
                newState ? (byte) 1 : (byte) 0);

        return newState;
    }

    public void incrementViolation(Player player) {
        violationCounts.merge(player.getUniqueId(), 1, Integer::sum);
        // Optional: Auto-kick or mute could go here later
    }

    public int getViolations(Player player) {
        return violationCounts.getOrDefault(player.getUniqueId(), 0);
    }

    public void notifyAdmins(Player violator, String reason, String message) {
        incrementViolation(violator);

        List<String> formatList = plugin.getConfig().getStringList("messages.admin-notify");
        if (formatList.isEmpty())
            return;

        for (String line : formatList) {
            String processed = line.replace("%player%", violator.getName())
                    .replace("%reason%", reason)
                    .replace("%message%", message);
            Component comp = legacySerializer.deserialize(processed);

            for (Player admin : Bukkit.getOnlinePlayers()) {
                // Check cache via getter
                if (admin.hasPermission("vchat.notify") && isNotifyEnabled(admin)) {
                    admin.sendMessage(comp);
                }
            }
        }
    }

    public void sendConfigMessage(CommandSender sender, String path) {
        if (plugin.getConfig().isString(path)) {
            String msg = plugin.getConfig().getString(path);
            if (msg != null && !msg.isEmpty()) {
                sender.sendMessage(legacySerializer.deserialize(msg));
            }
        } else {
            List<String> messages = plugin.getConfig().getStringList(path);
            for (String msg : messages) {
                sender.sendMessage(legacySerializer.deserialize(msg));
            }
        }
    }

    public void playSound(Player player, String path) {
        String soundName = plugin.getConfig().getString(path);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid sound: " + soundName);
            }
        }
    }
}
