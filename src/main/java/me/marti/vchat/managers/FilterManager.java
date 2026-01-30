package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import me.marti.vchat.checks.ChatFilter;
import me.marti.vchat.checks.FilterResult;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FilterManager {

    private final VChat plugin;
    private final List<ChatFilter> filters = new ArrayList<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public FilterManager(VChat plugin) {
        this.plugin = plugin;
        loadFilters();
    }

    public void loadFilters() {
        filters.clear();
        boolean enableAll = plugin.getConfig().getBoolean("filters.enable-all", true);
        if (!enableAll)
            return;

        // Register Filters (Instantiation happens here later)
        if (plugin.getConfig().getBoolean("filters.spam.enabled", true)) {
            filters.add(new me.marti.vchat.checks.SpamFilter(plugin));
        }
        if (plugin.getConfig().getBoolean("filters.caps.enabled", true)) {
            filters.add(new me.marti.vchat.checks.CapsFilter(plugin));
        }
        if (plugin.getConfig().getBoolean("filters.ads.enabled", true)) {
            filters.add(new me.marti.vchat.checks.AdsFilter(plugin));
        }
        if (plugin.getConfig().getBoolean("filters.profanity.enabled", true)) {
            filters.add(new me.marti.vchat.checks.ProfanityFilter(plugin));
        }
    }

    public FilterResult process(Player player, String message) {
        if (player.hasPermission("vchat.bypass.filters")) {
            return FilterResult.allowed();
        }

        for (ChatFilter filter : filters) {
            // Bypass Checks
            if (player.hasPermission("vchat.bypass.all"))
                continue; // Bypass all

            if (filter instanceof me.marti.vchat.checks.SpamFilter && player.hasPermission("vchat.bypass.spam"))
                continue;
            if (filter instanceof me.marti.vchat.checks.CapsFilter && player.hasPermission("vchat.bypass.caps"))
                continue;
            if (filter instanceof me.marti.vchat.checks.AdsFilter && player.hasPermission("vchat.bypass.ads"))
                continue;
            if (filter instanceof me.marti.vchat.checks.ProfanityFilter
                    && player.hasPermission("vchat.bypass.profanity"))
                continue;

            FilterResult result = filter.check(player, message);

            if (result.state() == FilterResult.State.BLOCKED) {
                // Play sound
                plugin.getAdminManager().playSound(player, "sounds.blocked");

                // Notify Admins
                String reason = result.reason() != null ? result.reason() : filter.getClass().getSimpleName();
                plugin.getAdminManager().notifyAdmins(player, reason, message);

                // Log Violation
                plugin.getLogManager().logViolation(player.getName(), reason, message);

                // Send reason to player (handled by listener usually, but can do here)
                return result;
            }

            if (result.state() == FilterResult.State.MODIFIED) {
                message = result.modifiedMessage();
                if (result.reason() != null) {
                    // Also notify for modifications (Censorship)
                    // Play sound (maybe distinct? using blocked for now or silence)
                    // plugin.getAdminManager().playSound(player, "sounds.blocked");

                    String reason = result.reason();
                    plugin.getAdminManager().notifyAdmins(player, reason, message); // Notify w/ original message
                    plugin.getLogManager().logViolation(player.getName(), reason, message); // Log original
                }
            }
        }

        // If we got here, maybe modified or allowed
        return FilterResult.modified(null, message); // Final result with potentially modified message
    }
}
