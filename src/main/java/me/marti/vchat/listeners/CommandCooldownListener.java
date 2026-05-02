package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CommandCooldownListener implements Listener {

    private final VChat plugin;
    private final ConcurrentMap<UUID, Long> lastCommandAt = new ConcurrentHashMap<>();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    public CommandCooldownListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("command-cooldown.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("vchat.bypass.commandcooldown") || player.hasPermission("vchat.admin")) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.length() < 2 || message.charAt(0) != '/') {
            return;
        }

        String commandName = message.substring(1).split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (commandName.isEmpty()) {
            return;
        }

        Set<String> exempt = getExemptCommands();
        if (exempt.contains(commandName)) {
            return;
        }

        int cooldownSeconds = Math.max(1, plugin.getConfigManager().getMainConfig().getInt("command-cooldown.seconds", 3));
        long cooldownMs = cooldownSeconds * 1000L;
        long now = System.currentTimeMillis();
        long last = lastCommandAt.getOrDefault(player.getUniqueId(), 0L);
        long remainingMs = cooldownMs - (now - last);

        if (remainingMs > 0L) {
            event.setCancelled(true);
            double remaining = remainingMs / 1000.0;
            String template = plugin.getConfigManager().getMessages()
                    .getString("moderation.command-cooldown", "&cEspera %time%s antes de usar otro comando.");
            player.sendMessage(legacySerializer.deserialize(
                    template.replace("%time%", String.format(Locale.US, "%.1f", remaining))));
            return;
        }

        lastCommandAt.put(player.getUniqueId(), now);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastCommandAt.remove(event.getPlayer().getUniqueId());
    }

    private Set<String> getExemptCommands() {
        Set<String> result = new HashSet<>();
        for (String raw : plugin.getConfigManager().getMainConfig().getStringList("command-cooldown.exempt-commands")) {
            String cleaned = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            if (cleaned.startsWith("/")) {
                cleaned = cleaned.substring(1);
            }
            if (!cleaned.isEmpty()) {
                result.add(cleaned);
            }
        }
        return result;
    }
}

