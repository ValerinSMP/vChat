package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final VChat plugin;

    public JoinListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Load data async or sync? PDC is sync usually but fast.
        plugin.getAdminManager().loadData(event.getPlayer());
        plugin.getMentionManager().loadData(event.getPlayer());
        plugin.getPrivateMessageManager().loadData(event.getPlayer());
        plugin.getIgnoreManager().loadData(event.getPlayer());
    }
}
