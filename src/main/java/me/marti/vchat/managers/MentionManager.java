package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionManager {

    private final VChat plugin;
    private final org.bukkit.NamespacedKey mentionKey;
    private final java.util.Map<java.util.UUID, Boolean> mentionCache = new java.util.HashMap<>();

    public MentionManager(VChat plugin) {
        this.plugin = plugin;
        this.mentionKey = new org.bukkit.NamespacedKey(plugin, "mentions_enabled");
    }

    public void loadData(Player player) {
        boolean value = true; // Default
        if (player.getPersistentDataContainer().has(mentionKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
            value = player.getPersistentDataContainer().get(mentionKey,
                    org.bukkit.persistence.PersistentDataType.BYTE) == 1;
        }
        mentionCache.put(player.getUniqueId(), value);
    }

    public void unloadData(Player player) {
        mentionCache.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
    }

    public boolean areMentionsEnabled(Player player) {
        return mentionCache.getOrDefault(player.getUniqueId(), true);
    }

    public void setMentionsEnabled(Player player, boolean enabled) {
        // Update Cache
        mentionCache.put(player.getUniqueId(), enabled);
        // Update PDC
        if (enabled) {
            // To save space, we could remove key if default is true.
            // But explicit is safer for now.
            player.getPersistentDataContainer().set(mentionKey, org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) 1);
        } else {
            player.getPersistentDataContainer().set(mentionKey, org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) 0);
        }
    }

    public void toggleMentions(Player player) {
        boolean current = areMentionsEnabled(player);
        boolean newState = !current;
        setMentionsEnabled(player, newState);

        if (newState) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.mentions-enabled");
            plugin.getAdminManager().playSound(player, "sounds.toggle-on");
        } else {
            plugin.getAdminManager().sendConfigMessage(player, "messages.mentions-disabled");
            plugin.getAdminManager().playSound(player, "sounds.toggle-off");
        }
    }

    private final java.util.Map<java.util.UUID, Long> cooldowns = new java.util.HashMap<>();

    public String processMentions(Player sender, String message) {
        if (!plugin.getConfig().getBoolean("mentions.enabled"))
            return message;
        if (!sender.hasPermission("vchat.mention"))
            return message;

        Pattern mentionPattern = Pattern.compile("@(\\w+)");
        Matcher matcher = mentionPattern.matcher(message);

        StringBuffer sb = new StringBuffer();

        boolean anyMention = false;

        while (matcher.find()) {
            String targetName = matcher.group(1);
            Player target = Bukkit.getPlayerExact(targetName);

            // Check if target exists, can be seen, AND has mentions enabled
            if (target != null && !target.equals(sender) && sender.canSee(target) && areMentionsEnabled(target)) {
                String color = plugin.getConfig().getString("mentions.color", "&e");
                String replacement = color + "@" + targetName + "&r";

                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));

                // Only notify if cooldown passed
                if (!isOnCooldown(sender, target)) {
                    notifyTarget(sender, target);
                    anyMention = true;
                }
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("@" + targetName));
            }
        }
        matcher.appendTail(sb);

        if (anyMention) {
            setCooldown(sender);
        }

        return sb.toString();
    }

    private boolean isOnCooldown(Player sender, Player target) {
        // We can have a global cooldown per sender OR per sender-target pair.
        // Simple global cooldown per sender is usually best to prevent spamming many
        // people too.
        if (sender.hasPermission("vchat.bypass.cooldown"))
            return false;
        long last = cooldowns.getOrDefault(sender.getUniqueId(), 0L);
        int cooldownSec = plugin.getConfig().getInt("mentions.cooldown", 5);
        return (System.currentTimeMillis() - last) < (cooldownSec * 1000L);
    }

    private void setCooldown(Player sender) {
        cooldowns.put(sender.getUniqueId(), System.currentTimeMillis());
    }

    private void notifyTarget(Player sender, Player target) {
        // Sound
        String soundName = plugin.getConfig().getString("mentions.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            target.playSound(target.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception e) {
            // ignore invalid sound
        }

        // Actionbar
        String actionMsg = plugin.getConfig().getString("mentions.actionbar");
        if (actionMsg != null && !actionMsg.isEmpty()) {
            actionMsg = actionMsg.replace("%player%", sender.getName());
            target.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(actionMsg));
        }
    }
}
