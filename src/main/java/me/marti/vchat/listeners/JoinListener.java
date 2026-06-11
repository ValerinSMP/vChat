package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class JoinListener implements Listener {

    private final VChat plugin;

    public JoinListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getAdminManager().loadData(player);
        plugin.getMentionManager().loadData(player);
        plugin.getPrivateMessageManager().loadData(player);
        plugin.getIgnoreManager().loadData(player);

        // Suppress vanilla join message — Paper: joinMessage(null), Bukkit: setJoinMessage(null)
        try {
            event.getClass().getMethod("joinMessage", net.kyori.adventure.text.Component.class)
                    .invoke(event, (Object) null);
        } catch (Exception ignored) {
            event.setJoinMessage(null);
        }

        // Delay 1 tick so SuperVanish has time to apply vanish state before we check
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (isVanished(player)) return;
            plugin.getJoinQuitManager().handleJoin(player);
        });
    }

    private boolean isVanished(Player player) {
        try {
            Class<?> api = Class.forName("de.myzelyam.api.vanish.VanishAPI");
            return (boolean) api.getMethod("isInvisible", Player.class).invoke(null, player);
        } catch (Exception ignored) {
        }
        return false;
    }
}
