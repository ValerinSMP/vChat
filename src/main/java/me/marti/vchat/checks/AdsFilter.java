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

    private final java.util.List<Pattern> cachedWhitelist = new java.util.ArrayList<>();
    private final java.util.List<String> simpleWhitelist = new java.util.ArrayList<>();

    public AdsFilter(VChat plugin) {
        this.plugin = plugin;
        loadWhitelist();
    }

    private void loadWhitelist() {
        cachedWhitelist.clear();
        simpleWhitelist.clear();
        List<String> whitelist = plugin.getConfig().getStringList("filters.ads.whitelist");
        for (String w : whitelist) {
            // Heuristic to decide if regex or simple
            // For safety, let's assume everything is a regex if it compiles, else string?
            // User code previously allowed simple contains fallback.
            // We'll store string for contains check, and Pattern for regex check.
            simpleWhitelist.add(w.toLowerCase());
            try {
                cachedWhitelist.add(Pattern.compile(w, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                // Not a valid regex, just rely on simple contains
            }
        }
    }

    @Override
    public FilterResult check(Player player, String message) {
        // Whitelist check first
        String msgLower = message.toLowerCase();

        // Simple Check
        for (String w : simpleWhitelist) {
            if (msgLower.contains(w))
                return FilterResult.allowed();
        }

        // Regex Check
        for (Pattern p : cachedWhitelist) {
            if (p.matcher(message).find())
                return FilterResult.allowed();
        }

        Matcher ipMatcher = IP_PATTERN.matcher(message);
        if (ipMatcher.find()) {
            return FilterResult.blocked("Publicidad (IP: " + ipMatcher.group() + ")", getBlockMessage());
        }

        Matcher urlMatcher = URL_PATTERN.matcher(message);
        if (urlMatcher.find()) {
            // Basic domain check can be aggressive, so we rely on whitelisting common ones
            // if needed
            return FilterResult.blocked("Publicidad (Link: " + urlMatcher.group() + ")", getBlockMessage());
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
