package me.marti.vchat.checks;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AdsFilter implements ChatFilter {

    private final VChat plugin;
    // Simple but effective patterns for IPs and broad domains
    private static final Pattern IP_PATTERN = Pattern.compile(
            "((?<![0-9])(?:(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[ ]?[.,-:; ][ ]?(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[ ]?[.,-:; ][ ]?(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2})[ ]?[.,-:; ][ ]?(?:25[0-5]|2[0-4][0-9]|[0-1]?[0-9]{1,2}))(?![0-9]))");
    private static final Pattern URL_PATTERN = Pattern.compile("([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}");
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public AdsFilter(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public FilterResult check(Player player, String message) {
        // Whitelist check first
        List<String> whitelist = plugin.getConfig().getStringList("filters.ads.whitelist");
        for (String w : whitelist) {
            try {
                // Use case-insensitive matching for whitelist patterns
                if (Pattern.compile(w, Pattern.CASE_INSENSITIVE).matcher(message).find()) {
                    return FilterResult.allowed();
                }
            } catch (Exception e) {
                // Fallback to simple contains if regex fails or simple string intended
                if (message.toLowerCase().contains(w.toLowerCase()))
                    return FilterResult.allowed();
            }
        }

        Matcher ipMatcher = IP_PATTERN.matcher(message);
        if (ipMatcher.find()) {
            return FilterResult.blocked("Publicidad (IP)", getBlockMessage());
        }

        Matcher urlMatcher = URL_PATTERN.matcher(message);
        if (urlMatcher.find()) {
            // Basic domain check can be aggressive, so we rely on whitelisting common ones
            // if needed
            return FilterResult.blocked("Publicidad (Link)", getBlockMessage());
        }

        return FilterResult.allowed();
    }

    private Component getBlockMessage() {
        List<String> lines = plugin.getConfig().getStringList("filters.ads.lines");
        Component comp = Component.empty();
        for (String line : lines) {
            comp = comp.append(legacySerializer.deserialize(line)).append(Component.newline());
        }
        return comp;
    }
}
