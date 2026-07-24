package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminManager {

    private final VChat plugin;
    private final org.bukkit.NamespacedKey notifyKey;
    private final org.bukkit.NamespacedKey personalChatKey;
    private final org.bukkit.NamespacedKey deathMutedKey;
    private final Map<UUID, Boolean> notifyCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> personalChatCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> deathMutedCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> violationCounts = new ConcurrentHashMap<>();

    public AdminManager(VChat plugin) {
        this.plugin = plugin;
        this.notifyKey = new org.bukkit.NamespacedKey(plugin, "notify_enabled");
        this.personalChatKey = new org.bukkit.NamespacedKey(plugin, "personal_chat_muted");
        this.deathMutedKey = new org.bukkit.NamespacedKey(plugin, "death_muted");
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
        if (plugin.getRedisManager() != null) {
            plugin.getRedisManager().setChatMuteToggle(player.getUniqueId(), newState);
        }

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
             broadcastConfigMessage("moderation.chat-muted");
             for(Player p : Bukkit.getOnlinePlayers()) {
                 playSound(p, "sounds.toggle-off");
             }
        } else {
             broadcastConfigMessage("moderation.chat-unmuted");
             for(Player p : Bukkit.getOnlinePlayers()) {
                 playSound(p, "sounds.toggle-on");
             }
        }
    }

    public void broadcastConfigMessage(String path) {
        String msg = plugin.getConfigManager().getMessages().getString(path);
        if (msg != null && !msg.isEmpty()) {
            PlatformUtil.broadcast(PlatformUtil.MM.deserialize(msg));
        }
    }

    public void sendConfigActionBar(Player player, String path, TagResolver... resolvers) {
        if (plugin.getConfigManager().getMessages().isString(path)) {
            String msg = plugin.getConfigManager().getMessages().getString(path);
            if (msg != null && !msg.isEmpty()) {
                sendActionBarOrChat(player, PlatformUtil.MM.deserialize(msg, resolvers));
            }
        }
    }

    /**
     * Único punto de entrada para "avisos rápidos" (actionbar). TODO mensaje que hoy
     * o mañana quiera ir por actionbar debe pasar por acá — así el toggle
     * 'messages.yml: use-actionbar' realmente aplica a todo el plugin, no solo a los
     * mensajes que vienen de messages.yml por config path.
     */
    public void sendActionBarOrChat(Player player, Component component) {
        if (plugin.getConfigManager().getMessages().getBoolean("use-actionbar", true)) {
            PlatformUtil.sendActionBar(player, component);
        } else {
            PlatformUtil.sendMessage(player, component);
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
        // PDC local no viaja entre servers al saltar de red — Redis (último toggle hecho
        // en cualquier server del cluster) manda si hay dato, si no se queda con el local.
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            Boolean remote = plugin.getRedisManager().getRemoteChatMuteToggle(player.getUniqueId());
            if (remote != null) personalMuted = remote;
        }
        personalChatCache.put(player.getUniqueId(), personalMuted);

        // Load Death messages muted
        boolean deathMuted = false;
        if (player.getPersistentDataContainer().has(deathMutedKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            deathMuted = player.getPersistentDataContainer().get(deathMutedKey, org.bukkit.persistence.PersistentDataType.BYTE) == 1;
        }
        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            Boolean remote = plugin.getRedisManager().getRemoteDeathMuteToggle(player.getUniqueId());
            if (remote != null) deathMuted = remote;
        }
        deathMutedCache.put(player.getUniqueId(), deathMuted);
    }

    public void unloadData(Player player) {
        notifyCache.remove(player.getUniqueId());
        personalChatCache.remove(player.getUniqueId());
        deathMutedCache.remove(player.getUniqueId());
    }

    // Death messages toggle
    public boolean isDeathMuted(Player player) {
        return deathMutedCache.getOrDefault(player.getUniqueId(), false);
    }

    public boolean toggleDeath(Player player) {
        boolean newState = !isDeathMuted(player);
        deathMutedCache.put(player.getUniqueId(), newState);
        player.getPersistentDataContainer().set(deathMutedKey,
                org.bukkit.persistence.PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);
        if (plugin.getRedisManager() != null) {
            plugin.getRedisManager().setDeathMuteToggle(player.getUniqueId(), newState);
        }
        return newState;
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

        // Placeholders quedan sin parsear (Placeholder.unparsed) para que un reason/message
        // con tags MiniMessage de un jugador no se interprete como color/click/hover.
        TagResolver resolvers = TagResolver.resolver(
                Placeholder.unparsed("player", violator.getName()),
                Placeholder.unparsed("reason", reason),
                Placeholder.unparsed("message", message));

        for (String line : formatList) {
            Component comp = PlatformUtil.MM.deserialize(line, resolvers);

            for (Player admin : Bukkit.getOnlinePlayers()) {
                // Check cache via getter
                if (admin.hasPermission("vchat.notify") && isNotifyEnabled(admin)) {
                    PlatformUtil.sendMessage(admin, comp);
                }
            }
        }
    }

    public void sendConfigMessage(CommandSender sender, String path, TagResolver... resolvers) {
        if (plugin.getConfigManager().getMessages().isString(path)) {
            String msg = plugin.getConfigManager().getMessages().getString(path);
            if (msg != null && !msg.isEmpty()) {
                PlatformUtil.sendMessage(sender, PlatformUtil.MM.deserialize(msg, resolvers));
            }
        } else {
            List<String> messages = plugin.getConfigManager().getMessages().getStringList(path);
            for (String msg : messages) {
                PlatformUtil.sendMessage(sender, PlatformUtil.MM.deserialize(msg, resolvers));
            }
        }
    }

    public void playSound(Player player, String path) {
        String soundName = plugin.getConfigManager().getMainConfig().getString(path);
        if (soundName != null && !soundName.isEmpty()) {
            Sound sound = PlatformUtil.resolveSound(soundName);
            if (sound != null) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            else plugin.getLogger().warning("Invalid sound: " + soundName);
        }
    }
}
