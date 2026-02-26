package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import me.marti.vchat.checks.ChatFilter;
import me.marti.vchat.checks.FilterResult;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FilterManager {

    private final VChat plugin;
    private final List<ChatFilter> filters = new ArrayList<>();

    public FilterManager(VChat plugin) {
        this.plugin = plugin;
        loadFilters();
    }

    public void loadFilters() {
        filters.clear();
        boolean enableAll = plugin.getConfigManager().getFilters().getBoolean("enable-all", true);
        if (!enableAll)
            return;

        if (plugin.getConfigManager().getFilters().getBoolean("spam.enabled", true)) {
            filters.add(new me.marti.vchat.checks.SpamFilter(plugin));
        }
        if (plugin.getConfigManager().getFilters().getBoolean("caps.enabled", true)) {
            filters.add(new me.marti.vchat.checks.CapsFilter(plugin));
        }
        if (plugin.getConfigManager().getFilters().getBoolean("ads.enabled", true)) {
            filters.add(new me.marti.vchat.checks.AdsFilter(plugin));
        }
        if (plugin.getConfigManager().getFilters().getBoolean("profanity.enabled", true)) {
            filters.add(new me.marti.vchat.checks.ProfanityFilter(plugin));
        }
    }

    public FilterResult process(Player player, String message) {
        String originalMessage = message;
        String lastReason = null;

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
                return result;
            }

            if (result.state() == FilterResult.State.MODIFIED) {
                message = result.modifiedMessage();
                if (result.reason() != null) {
                    lastReason = result.reason();
                }
            }
        }

        if (!message.equals(originalMessage)) {
            return FilterResult.modified(lastReason, message);
        }

        return FilterResult.allowed();
    }
}
