package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import me.marti.vchat.checks.FilterResult;
import me.marti.vchat.managers.FilterManager;
import me.marti.vchat.managers.FormatManager;
import me.marti.vchat.managers.MentionManager;
import me.marti.vchat.processors.MessageProcessor;
import me.marti.vchat.utils.MessageSanitizer;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Set;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class ChatListener implements Listener {

    private final VChat plugin;
    private final FormatManager formatManager;
    private final MessageProcessor messageProcessor;
    private final FilterManager filterManager;
    private final MentionManager mentionManager;

    public ChatListener(VChat plugin, FormatManager formatManager, MessageProcessor messageProcessor,
            FilterManager filterManager, MentionManager mentionManager) {
        this.plugin = plugin;
        this.formatManager = formatManager;
        this.messageProcessor = messageProcessor;
        this.filterManager = filterManager;
        this.mentionManager = mentionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Check Global Mute
        if (plugin.getAdminManager().isGlobalChatMuted() && !player.hasPermission("vchat.bypass.togglechat")) {
            event.setCancelled(true);
            runSync(() -> plugin.getAdminManager().sendConfigMessage(player, "moderation.chat-muted"));
            return;
        }

        String originalMessage = event.getMessage();
        String message = originalMessage;

        // 1. PRE-SANITIES
        message = MessageSanitizer.prepare(player, message);

        MentionManager.MentionProcessResult mentionResult = mentionManager.processMentions(player, message);
        message = mentionResult.processedMessage();

        // 3. ITEM PLACEHOLDER
        boolean hasItemPlaceholder = message.toLowerCase().contains("[item]") || message.toLowerCase().contains("[i]");
        Component itemComp = null;
        if (hasItemPlaceholder) {
            org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() != org.bukkit.Material.AIR) {
                itemComp = messageProcessor.getItemComponent(player, hand);
                message = message.replaceAll("(?i)\\[item]|(?i)\\[i]", "<item_tag>");
            }
        }

        // 4. FILTERS
        FilterResult result = filterManager.process(player, message);
        if (result.state() == FilterResult.State.BLOCKED) {
            event.setCancelled(true);
            final String fOriginal = originalMessage;
            runSync(() -> {
                plugin.getAdminManager().playSound(player, "sounds.blocked");
                if (result.reason() != null) {
                    plugin.getAdminManager().notifyAdmins(player, result.reason(), fOriginal);
                    plugin.getLogManager().logViolation(player.getName(), result.reason(), fOriginal);
                }
                if (result.reasonMessage() != null) {
                    PlatformUtil.sendMessage(player, result.reasonMessage());
                }
            });
            return;
        }
        if (result.state() == FilterResult.State.MODIFIED) {
            if (result.reason() != null) {
                String modifiedMessage = result.modifiedMessage();
                runSync(() -> plugin.getAdminManager().notifyAdmins(player, result.reason(), originalMessage));
                plugin.getLogManager().logViolation(player.getName(), result.reason(), originalMessage);
                message = modifiedMessage;
            } else {
                message = result.modifiedMessage();
            }
        }

        // 5. PARSE TO COMPONENT
        Component messageComponent = MessageSanitizer.parse(player, message, itemComp);

        // 6. FORMATTING
        String format = formatManager.getFormat(player);
        Component formatted = messageProcessor.process(player, format, messageComponent);
        plugin.getDiscordBridgeManager().relayMinecraftChat(player,
                PlainTextComponentSerializer.plainText().serialize(messageComponent));

        // Cancel vanilla broadcast and send manually (replaces Paper renderer/viewers)
        event.setCancelled(true);

        Set<UUID> targetsToNotify = mentionResult.targetsToNotify();
        String legacyFormatted = LegacyComponentSerializer.legacySection().serialize(formatted);

        runSync(() -> {
            for (Player audience : Bukkit.getOnlinePlayers()) {
                if (plugin.getIgnoreManager().isIgnored(audience.getUniqueId(), player.getUniqueId())
                        && !player.hasPermission("vchat.bypass.ignore")) {
                    continue;
                }
                if (plugin.getAdminManager().isPersonalChatMuted(audience)) {
                    continue;
                }
                PlatformUtil.sendMessage(audience, formatted);
            }
            // Also send to console
            PlatformUtil.sendMessage(Bukkit.getConsoleSender(), formatted);

            if (!targetsToNotify.isEmpty()) {
                mentionManager.notifyTargets(player, targetsToNotify);
            }
        });
    }

    private void runSync(Runnable runnable) {
        if (plugin.getServer().isPrimaryThread()) {
            runnable.run();
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, runnable);
    }
}
