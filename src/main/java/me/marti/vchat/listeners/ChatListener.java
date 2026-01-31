package me.marti.vchat.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.marti.vchat.VChat;
import me.marti.vchat.checks.FilterResult;
import me.marti.vchat.managers.FilterManager;
import me.marti.vchat.managers.FormatManager;
import me.marti.vchat.managers.MentionManager;
import me.marti.vchat.processors.MessageProcessor;
import me.marti.vchat.utils.MessageSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Check Global Mute
        if (plugin.getAdminManager().isGlobalChatMuted() && !player.hasPermission("vchat.bypass.togglechat")) {
            event.setCancelled(true);
            plugin.getAdminManager().sendConfigMessage(player, "moderation.chat-muted");
            return;
        }

        Component originalMessageComp = event.message();
        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(originalMessageComp);

        // 1. PRE-SANITIES (Escape & Transition)
        message = MessageSanitizer.prepare(player, message);

        // 2. MENTIONS
        message = mentionManager.processMentions(player, message);

        // 3. ITEM PLACEHOLDER
        boolean hasItemPlaceholder = message.toLowerCase().contains("[item]") || message.toLowerCase().contains("[i]");
        Component itemComp = null;
        if (hasItemPlaceholder) {
            org.bukkit.inventory.ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() != org.bukkit.Material.AIR) {
                itemComp = messageProcessor.getItemComponent(hand);
                message = message.replaceAll("(?i)\\[item]|(?i)\\[i]", "<item_tag>");
            }
        }

        // 4. FILTERS (on string result)
        FilterResult result = filterManager.process(player, message);
        if (result.state() == FilterResult.State.BLOCKED) {
            event.setCancelled(true);
            if (result.reasonMessage() != null) {
                player.sendMessage(result.reasonMessage());
            }
            return;
        }
        if (result.state() == FilterResult.State.MODIFIED) {
            message = result.modifiedMessage();
        }

        // 5. PARSE TO COMPONENT (Enforces Permissions)
        Component messageComponent = MessageSanitizer.parse(player, message, itemComp);

        // 6. FORMATTING
        String format = formatManager.getFormat(player);
        Component formatted = messageProcessor.process(player, format, messageComponent);

        event.renderer((source, sourceDisplayName, messageComp, viewer) -> formatted);

        // Handle Ignored Players and Personal Mute
        event.viewers().removeIf(viewer -> {
            if (viewer instanceof Player audience) {
                // Check Ignored
                if (plugin.getIgnoreManager().isIgnored(audience.getUniqueId(), player.getUniqueId())
                        && !audience.hasPermission("vchat.bypass.ignore")) {
                    return true;
                }

                // Check Personal Chat Toggle
                if (plugin.getAdminManager().isPersonalChatMuted(audience)) {
                    return true;
                }
            }
            return false;
        });
    }
}
