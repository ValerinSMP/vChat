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
            return enabled ? plugin.getConfig().getString("messages.placeholders.enabled", "&aActivado")
                    : plugin.getConfig().getString("messages.placeholders.disabled", "&cDesactivado");
        }

        // %vchat_mentions_status%
        if (params.equalsIgnoreCase("mentions_status")) {
            boolean enabled = plugin.getMentionManager().areMentionsEnabled(player);
            return enabled ? plugin.getConfig().getString("messages.placeholders.enabled", "&aActivado")
                    : plugin.getConfig().getString("messages.placeholders.disabled", "&cDesactivado");
        }

        return null;
    }
}
