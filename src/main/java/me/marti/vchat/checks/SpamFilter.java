package me.marti.vchat.checks;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SpamFilter implements ChatFilter {

    private final VChat plugin;
    private final Map<UUID, String> lastMessages = new HashMap<>();
    private final Map<UUID, Long> lastMessageTime = new HashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public SpamFilter(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public FilterResult check(Player player, String message) {
        UUID uuid = player.getUniqueId();
        String last = lastMessages.get(uuid);
        long now = System.currentTimeMillis();
        long lastTime = lastMessageTime.getOrDefault(uuid, 0L);

        // Cooldown check (Simple time based)
        int cooldownSeconds = plugin.getConfig().getInt("filters.spam.cooldown", 3);
        long diff = now - lastTime;

        if (last != null && diff < (cooldownSeconds * 1000L)) {
            // Check similarity
            int threshold = plugin.getConfig().getInt("filters.spam.similarity-threshold", 80);
            double similarity = getSimilarity(last, message);

            if (similarity >= threshold) {
                // BLOCKED
                Component reasonMsg = getBlockMessage(cooldownSeconds - (diff / 1000.0));
                return FilterResult.blocked("Spam (Similitud: " + (int) similarity + "%)", reasonMsg);
            }
        }

        lastMessages.put(uuid, message);
        lastMessageTime.put(uuid, now);

        return FilterResult.allowed();
    }

    private Component getBlockMessage(double timeLeft) {
        List<String> lines = plugin.getConfig().getStringList("filters.spam.lines");
        Component comp = Component.empty();
        for (String line : lines) {
            String processed = line.replace("%time%", String.format("%.1f", timeLeft));
            comp = comp.append(legacySerializer.deserialize(processed)).append(Component.newline());
        }
        return comp;
    }

    // Levenshtein / Similarity logic
    private double getSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { // longer should always have greater length
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 100.0;
            /* both empty */ }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength * 100.0;
    }

    private int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0)
                    costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1))
                            newValue = Math.min(Math.min(newValue, lastValue),
                                    costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0)
                costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }
}
