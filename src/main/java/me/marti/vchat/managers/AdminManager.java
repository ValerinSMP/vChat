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
    private final org.bukkit.NamespacedKey personalChatKey; // New key
    private final Map<UUID, Boolean> notifyCache = new HashMap<>();
    private final Map<UUID, Boolean> personalChatCache = new HashMap<>(); // New cache
    private final Map<UUID, Integer> violationCounts = new HashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand(); 

    public AdminManager(VChat plugin) {
        this.plugin = plugin;
        this.notifyKey = new org.bukkit.NamespacedKey(plugin, "notify_enabled");
        this.personalChatKey = new org.bukkit.NamespacedKey(plugin, "personal_chat_muted");
    }
    
    // Global Chat Logic
    private boolean globalChatMuted = false;

    public boolean isGlobalChatMuted() {
        return globalChatMuted;
    }
    
    // Personal Chat Logic
    public boolean isPersonalChatMuted(Player player) {
        return personalChatCache.getOrDefault(player.getUniqueId(), false);
    }
    
    public void togglePersonalChat(Player player) {
        boolean newState = !isPersonalChatMuted(player);
        personalChatCache.put(player.getUniqueId(), newState);
        player.getPersistentDataContainer().set(personalChatKey, org.bukkit.persistence.PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);
        
        if (newState) {
            // Now muted
            sendConfigActionBar(player, "moderation.personal-chat-disabled");
            playSound(player, "sounds.toggle-off");
        } else {
             // Now unmuted
            sendConfigActionBar(player, "moderation.personal-chat-enabled");
            playSound(player, "sounds.toggle-on");
        }
    }

    public void setGlobalChatMuted(boolean globalChatMuted) {
        this.globalChatMuted = globalChatMuted;
    }

    public void toggleGlobalChat() {
        this.globalChatMuted = !this.globalChatMuted;
        if (globalChatMuted) {
             org.bukkit.Bukkit.broadcast(legacySerializer.deserialize(plugin.getConfigManager().getMessages().getString("moderation.chat-muted", "&cChat silenciado.")));
             // Broadcast sound
             for(Player p : Bukkit.getOnlinePlayers()) {
                 playSound(p, "sounds.toggle-off");
             }
        } else {
             org.bukkit.Bukkit.broadcast(legacySerializer.deserialize(plugin.getConfigManager().getMessages().getString("moderation.chat-unmuted", "&aChat activado.")));
             for(Player p : Bukkit.getOnlinePlayers()) {
                 playSound(p, "sounds.toggle-on");
             }
        }
    }
    
    public void sendConfigActionBar(Player player, String path) {
         if (plugin.getConfigManager().getMessages().isString(path)) {
            String msg = plugin.getConfigManager().getMessages().getString(path);
            if (msg != null && !msg.isEmpty()) {
                player.sendActionBar(legacySerializer.deserialize(msg));
            }
        }
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
        
        // Load Personal Chat
        boolean personalMuted = false;
        if (player.getPersistentDataContainer().has(personalChatKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
             personalMuted = player.getPersistentDataContainer().get(personalChatKey, org.bukkit.persistence.PersistentDataType.BYTE) == 1;
        }
        personalChatCache.put(player.getUniqueId(), personalMuted);
    }

    public void unloadData(Player player) {
        notifyCache.remove(player.getUniqueId());
        personalChatCache.remove(player.getUniqueId());
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

        List<String> formatList = plugin.getConfigManager().getMessages().getStringList("admin-notify");
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
        // Defaulting to messages.yml for messages
        if (plugin.getConfigManager().getMessages().isString(path)) {
            String msg = plugin.getConfigManager().getMessages().getString(path);
            if (msg != null && !msg.isEmpty()) {
                sender.sendMessage(legacySerializer.deserialize(msg));
            }
        } else {
            List<String> messages = plugin.getConfigManager().getMessages().getStringList(path);
            for (String msg : messages) {
                sender.sendMessage(legacySerializer.deserialize(msg));
            }
        }
    }

    public void playSound(Player player, String path) {
        // Defaulting to config.yml for general sounds
        String soundName = plugin.getConfigManager().getMainConfig().getString(path);
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
