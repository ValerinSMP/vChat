package me.marti.vchat.checks;

import me.marti.vchat.VChat;
import org.bukkit.entity.Player;

public class CapsFilter implements ChatFilter {

    private final VChat plugin;

    public CapsFilter(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public FilterResult check(Player player, String message) {
        int minLength = plugin.getConfig().getInt("filters.caps.min-length", 5);
        if (message.length() < minLength)
            return FilterResult.allowed();

        int threshold = plugin.getConfig().getInt("filters.caps.percentage", 50);
        int capsCount = 0;

        // Count letters only
        int lettersCount = 0;

        for (char c : message.toCharArray()) {
            if (Character.isLetter(c)) {
                lettersCount++;
                if (Character.isUpperCase(c)) {
                    capsCount++;
                }
            }
        }

        if (lettersCount == 0)
            return FilterResult.allowed();

        double percentage = (double) capsCount / lettersCount * 100;

        if (percentage > threshold) {
            return FilterResult.modified("Mayúsculas (" + (int) percentage + "%)", message.toLowerCase());
        }

        return FilterResult.allowed();
    }
}
