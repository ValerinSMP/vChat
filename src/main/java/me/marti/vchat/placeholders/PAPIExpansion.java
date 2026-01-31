package me.marti.vchat.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.marti.vchat.VChat;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PAPIExpansion extends PlaceholderExpansion {

    private final VChat plugin;

    public PAPIExpansion(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "vchat";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null)
            return "";

        // %vchat_notify_status%
        if (params.equalsIgnoreCase("notify_status")) {
            boolean enabled = plugin.getAdminManager().isNotifyEnabled(player);
            return getStatusString(enabled);
        }

        // %vchat_mentions_status%
        if (params.equalsIgnoreCase("mentions_status") || params.equalsIgnoreCase("toggle_mentions")) {
            boolean enabled = plugin.getMentionManager().areMentionsEnabled(player);
            return getStatusString(enabled);
        }
        
        // %vchat_toggle_msg%
        if (params.equalsIgnoreCase("toggle_msg")) {
             boolean enabled = plugin.getPrivateMessageManager().isMsgEnabled(player);
             return getStatusString(enabled);
        }

        // %vchat_toggle_spy%
        if (params.equalsIgnoreCase("toggle_spy")) {
             boolean enabled = plugin.getPrivateMessageManager().isSpyEnabled(player);
             return getStatusString(enabled);
        }
        
        // %vchat_toggle_chat%
        if (params.equalsIgnoreCase("toggle_chat")) {
             // isPersonalChatMuted = true means chat is HIDDEN (disabled) for this player.
             // We return "Enabled" if NOT muted.
             boolean isMuted = plugin.getAdminManager().isPersonalChatMuted(player);
             return getStatusString(!isMuted); 
        }
        
        // %vchat_toggle_notify%
        if (params.equalsIgnoreCase("toggle_notify")) {
             boolean enabled = plugin.getAdminManager().isNotifyEnabled(player);
             return getStatusString(enabled);
        }

        return null;
    }

    private String getStatusString(boolean enabled) {
        return enabled ? plugin.getConfigManager().getMessages().getString("placeholders.enabled", "true")
                       : plugin.getConfigManager().getMessages().getString("placeholders.disabled", "false");
    }
}
