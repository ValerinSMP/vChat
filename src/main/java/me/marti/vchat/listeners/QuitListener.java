package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {

    private final VChat plugin;

    public QuitListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getAdminManager().unloadData(event.getPlayer());
        plugin.getMentionManager().unloadData(event.getPlayer());
        plugin.getPrivateMessageManager().unloadData(event.getPlayer());
        plugin.getIgnoreManager().unloadData(event.getPlayer());
    }
}
