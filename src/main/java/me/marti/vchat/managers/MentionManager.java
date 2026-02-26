package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MentionManager {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([.\\w]+)");

    private final VChat plugin;
    private final org.bukkit.NamespacedKey mentionKey;
    private final ConcurrentMap<UUID, Boolean> mentionCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> onlineNameIndex = new ConcurrentHashMap<>();

    public MentionManager(VChat plugin) {
        this.plugin = plugin;
        this.mentionKey = new org.bukkit.NamespacedKey(plugin, "mentions_enabled");
    }

    public void loadData(Player player) {
        boolean value = true;
        Byte raw = player.getPersistentDataContainer().get(mentionKey, PersistentDataType.BYTE);
        if (raw != null) {
            value = raw == 1;
        }
        mentionCache.put(player.getUniqueId(), value);
        onlineNameIndex.put(player.getName().toLowerCase(Locale.ROOT), player.getUniqueId());
    }

    public void unloadData(Player player) {
        mentionCache.remove(player.getUniqueId());
        cooldowns.remove(player.getUniqueId());
        onlineNameIndex.remove(player.getName().toLowerCase(Locale.ROOT));
    }

    public boolean areMentionsEnabled(Player player) {
        return mentionCache.getOrDefault(player.getUniqueId(), true);
    }

    public void setMentionsEnabled(Player player, boolean enabled) {
        mentionCache.put(player.getUniqueId(), enabled);
        player.getPersistentDataContainer().set(mentionKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    public void toggleMentions(Player player) {
        boolean newState = !areMentionsEnabled(player);
        setMentionsEnabled(player, newState);

        if (newState) {
            plugin.getAdminManager().sendConfigActionBar(player, "messages.mentions-enabled");
            plugin.getAdminManager().playSound(player, "sounds.toggle-on");
        } else {
            plugin.getAdminManager().sendConfigActionBar(player, "messages.mentions-disabled");
            plugin.getAdminManager().playSound(player, "sounds.toggle-off");
        }
    }

    public MentionProcessResult processMentions(Player sender, String message) {
        if (!plugin.getConfigManager().getMentions().getBoolean("enabled", true) || !sender.hasPermission("vchat.mention")) {
            return new MentionProcessResult(message, Collections.emptySet());
        }

        Matcher matcher = MENTION_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();

        UUID senderId = sender.getUniqueId();
        Set<UUID> targetsToNotify = new HashSet<>();
        boolean applyCooldown = !sender.hasPermission("vchat.bypass.cooldown");
        boolean onCooldown = applyCooldown && isOnCooldown(senderId);

        String color = resolveMentionColor();

        while (matcher.find()) {
            String targetName = matcher.group(1);
            UUID targetId = onlineNameIndex.get(targetName.toLowerCase(Locale.ROOT));

            if (targetId != null && !targetId.equals(senderId) && mentionCache.getOrDefault(targetId, true)) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(color + "@" + targetName + "<reset>"));
                if (!onCooldown) {
                    targetsToNotify.add(targetId);
                }
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement("@" + targetName));
            }
        }
        matcher.appendTail(sb);

        if (!targetsToNotify.isEmpty() && applyCooldown) {
            cooldowns.put(senderId, System.currentTimeMillis());
        }

        return new MentionProcessResult(sb.toString(), targetsToNotify);
    }

    public void notifyTargets(Player sender, Set<UUID> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return;
        }

        String soundName = plugin.getConfigManager().getMentions().getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        Sound sound = null;
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
        }

        String actionMsg = plugin.getConfigManager().getMentions().getString("actionbar");

        for (UUID targetId : targetIds) {
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !areMentionsEnabled(target)) {
                continue;
            }

            if (sound != null) {
                target.playSound(target.getLocation(), sound, 1.0f, 1.0f);
            }

            if (actionMsg != null && !actionMsg.isEmpty()) {
                String rendered = actionMsg.replace("%player%", sender.getName());
                target.sendActionBar(LegacyComponentSerializer.legacyAmpersand().deserialize(rendered));
            }
        }
    }

    private boolean isOnCooldown(UUID senderId) {
        long last = cooldowns.getOrDefault(senderId, 0L);
        int cooldownSec = plugin.getConfigManager().getMentions().getInt("cooldown", 5);
        return (System.currentTimeMillis() - last) < (cooldownSec * 1000L);
    }

    private String resolveMentionColor() {
        String color = plugin.getConfigManager().getMentions().getString("color", "<yellow>");
        if (color.startsWith("&")) {
            color = color.replace("&e", "<yellow>")
                    .replace("&a", "<green>")
                    .replace("&b", "<aqua>")
                    .replace("&c", "<red>")
                    .replace("&d", "<light_purple>")
                    .replace("&f", "<white>")
                    .replace("&r", "<reset>");
        }
        return color;
    }

    public record MentionProcessResult(String processedMessage, Set<UUID> targetsToNotify) {
    }
}
