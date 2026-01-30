package me.marti.vchat.checks;

import me.marti.vchat.VChat;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProfanityFilter implements ChatFilter {

    private final VChat plugin;

    public ProfanityFilter(VChat plugin) {
        this.plugin = plugin;
    }

    private final java.util.Map<String, Pattern> patternCache = new java.util.HashMap<>();

    @Override
    public FilterResult check(Player player, String message) {
        List<String> badWords = plugin.getConfig().getStringList("filters.profanity.words");
        if (badWords.isEmpty())
            return FilterResult.allowed();

        boolean modified = false;
        String tempMessage = message;

        for (String word : badWords) {
            // Get or create robust pattern
            Pattern p = patternCache.computeIfAbsent(word, this::createRobustPattern);

            Matcher m = p.matcher(tempMessage);
            if (m.find()) {
                // Replace with ****
                // reset matcher to start to handle multiple
                m.reset();
                StringBuffer sb = new StringBuffer();
                while (m.find()) {
                    m.appendReplacement(sb, "****");
                }
                m.appendTail(sb);
                tempMessage = sb.toString();
                modified = true;
            }
        }

        if (modified) {
            return FilterResult.modified("Groserías", tempMessage);
        }

        return FilterResult.allowed();
    }

    // Generates a regex that matches leetspeak and repeated chars
    // e.g. "nigger" -> "n+[1i|l!]+g+g+3+r+" (with garbage checks if desired, but
    // strict spacing is better for chat)
    private Pattern createRobustPattern(String word) {
        StringBuilder sb = new StringBuilder();
        sb.append("(?i)"); // Case insensitive

        for (char c : word.toCharArray()) {
            sb.append(getRegexForChar(c));
            sb.append("+"); // Allow repeats (niggggger)
            sb.append("[^a-zA-Z0-9]*"); // Allow non-alphanum noise (n.i.g)
        }

        // Remove last noise matcher to avoid trailing match issues if needed, but
        // usually fine.
        // Actually the loop appends noise AFTER each char. "r" -> "r+" + "noise".
        // We probably only want noise BETWEEN chars.
        // Let's refactor loop.

        StringBuilder robust = new StringBuilder();
        robust.append("(?i)");
        char[] chars = word.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            robust.append(getRegexForChar(chars[i]));
            robust.append("+"); // One or more of this char/leet

            if (i < chars.length - 1) {
                robust.append("[^a-zA-Z0-9]*"); // Allow noise between chars only
            }
        }

        return Pattern.compile(robust.toString());
    }

    private String getRegexForChar(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'a' -> "[a4@]";
            case 'b' -> "[b8]";
            case 'c' -> "[c(\\[]";
            case 'e' -> "[e3]";
            case 'g' -> "[g69]";
            case 'h' -> "[h#]";
            case 'i' -> "[i1l!]";
            case 'o' -> "[o0]";
            case 's' -> "[s5$]";
            case 't' -> "[t7+]";
            case 'z' -> "[z2]";
            default -> Pattern.quote(String.valueOf(c));
        };
    }

    // Old methods removed

}
