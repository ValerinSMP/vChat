package me.marti.vchat.checks;

import org.bukkit.entity.Player;

public interface ChatFilter {
    FilterResult check(Player player, String message);
}
