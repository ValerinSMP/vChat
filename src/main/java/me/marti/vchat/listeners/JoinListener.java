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

        // Chequear presencia ANTES de sobreescribirla: si ya figuraba online en otro
        // server del cluster, este join es solo un salto de red (hub -> survival, etc),
        // no una conexión nueva — el anuncio de "se unió" ya se mostró en su primer server.
        plugin.loadPlayerState(player, () -> {
            if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
                plugin.getRedisManager().registerPresence(player.getUniqueId(), player.getName(), alreadyInNetwork -> {
                    if (!player.isOnline() || isVanished(player) || alreadyInNetwork) return;
                    plugin.getJoinQuitManager().handleJoin(player);
                });
            } else if (!isVanished(player)) {
                plugin.getJoinQuitManager().handleJoin(player);
            }
        });

        // Suppress vanilla join message — Paper: joinMessage(null), Bukkit: setJoinMessage(null)
        try {
            event.getClass().getMethod("joinMessage", net.kyori.adventure.text.Component.class)
                    .invoke(event, (Object) null);
        } catch (Exception ignored) {
            event.setJoinMessage(null);
        }

    }

    private boolean isVanished(Player player) {
        // CobbleCommands vanish integration.
        try {
            Class<?> api = Class.forName("com.cobble.commands.CobbleAPI");
            return (boolean) api.getMethod("isVanished", Player.class).invoke(null, player);
        } catch (Exception ignored) {
        }
        // SuperVanish fallback
        try {
            Class<?> api = Class.forName("de.myzelyam.api.vanish.VanishAPI");
            return (boolean) api.getMethod("isInvisible", Player.class).invoke(null, player);
        } catch (Exception ignored) {
        }
        return false;
    }
}
