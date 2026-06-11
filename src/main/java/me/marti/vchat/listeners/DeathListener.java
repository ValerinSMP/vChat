package me.marti.vchat.listeners;

import me.clip.placeholderapi.PlaceholderAPI;
import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

public class DeathListener implements Listener {

    private final VChat plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    public DeathListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getConfigManager().getMessages().getBoolean("death.enabled", true)) return;

        // Suppress vanilla death message — Paper: deathMessage(null), Bukkit: setDeathMessage(null)
        try {
            event.getClass().getMethod("deathMessage", net.kyori.adventure.text.Component.class)
                    .invoke(event, (Object) null);
        } catch (Exception ignored) {
            event.setDeathMessage(null);
        }

        Player victim = event.getEntity();
        Entity killerEntity = victim.getKiller();

        Component message;
        if (killerEntity instanceof Player killer) {
            String format = plugin.getConfigManager().getMessages().getString(
                    "death.pvp-format",
                    "<color:#FF6961>☠ <lp_victim> <gray>fue asesinado por <color:#FF6961>🗡 <lp_killer>");
            message = build(format, victim, killer);
        } else {
            String format = plugin.getConfigManager().getMessages().getString(
                    "death.pve-format",
                    "<color:#FF6961>☠ <lp_victim> <gray>ha muerto");
            message = build(format, victim, null);
        }

        for (Player online : Bukkit.getOnlinePlayers()) {
            PlatformUtil.sendMessage(online, message);
        }
        PlatformUtil.sendMessage(Bukkit.getConsoleSender(), message);
    }

    private Component build(String format, Player victim, Player killer) {
        String victimPrefix = getPrefix(victim);
        String killerPrefix = killer != null ? getPrefix(killer) : "";

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            victimPrefix = PlaceholderAPI.setPlaceholders(victim, victimPrefix);
            format = PlaceholderAPI.setPlaceholders(victim, format);
        }

        Component victimComp = legacyAmp.deserialize(victimPrefix)
                .append(Component.text(victim.getName()));
        Component killerComp = killer != null
                ? legacyAmp.deserialize(killerPrefix).append(Component.text(killer.getName()))
                : Component.empty();

        return mm.deserialize(format,
                Placeholder.component("lp_victim", victimComp),
                Placeholder.component("lp_killer", killerComp));
    }

    private String getPrefix(Player player) {
        net.luckperms.api.cacheddata.CachedMetaData meta =
                plugin.getLuckPerms().getPlayerAdapter(Player.class).getMetaData(player);
        return meta.getPrefix() != null ? meta.getPrefix() : "";
    }
}
