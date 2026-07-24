package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuitListener implements Listener {

    private final VChat plugin;

    public QuitListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        try {
            event.getClass().getMethod("quitMessage", net.kyori.adventure.text.Component.class)
                    .invoke(event, (Object) null);
        } catch (Exception ignored) {
            event.setQuitMessage(null);
        }

        plugin.getAdminManager().unloadData(event.getPlayer());
        plugin.getMentionManager().unloadData(event.getPlayer());
        plugin.getPrivateMessageManager().unloadData(event.getPlayer());
        plugin.getIgnoreManager().unloadData(event.getPlayer());

        if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
            String name = event.getPlayer().getName();
            String thisServer = plugin.getRedisManager().getServerId();
            // Delay de gracia: si esto es un salto hub -> survival dentro del mismo cluster,
            // el server destino ya va a haber sobrescrito la presencia para cuando esto corra.
            // Sin el delay, este quit borraría esa presencia recién puesta y el join del otro
            // server jamás vería que el jugador "ya estaba en la red".
            plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin,
                    () -> plugin.getRedisManager().clearPlayerIfServerMatches(name, thisServer), 100L);
        }
    }
}
