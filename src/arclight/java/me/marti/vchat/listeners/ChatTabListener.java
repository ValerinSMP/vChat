package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import org.bukkit.event.Listener;

/**
 * No-op tab listener for Arclight. Mention tab completion is handled by
 * MentionsTabInjector (ProtocolLib) when available. TabCompleteEvent was
 * removed from Spigot 1.21; AsyncTabCompleteEvent is Paper-only.
 */
public class ChatTabListener implements Listener {

    public ChatTabListener(VChat plugin) {
        // intentionally empty
    }
}
