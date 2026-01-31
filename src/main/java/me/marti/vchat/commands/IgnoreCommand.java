package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class IgnoreCommand implements CommandExecutor {

    private final VChat plugin;

    public IgnoreCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Solo jugadores.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("vchat.ignore")) {
            // Check default perm in plugin.yml usually true for everyone
            plugin.getAdminManager().sendConfigMessage(player, "messages.no-permission");
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(Component.text("Uso: /ignore <jugador>", NamedTextColor.RED));
            return true;
        }

        // Handle offline players? Persistence supports UUIDs, so we ideally resolve
        // UUID.
        // For now, let's require online or use Bukkit.getOfflinePlayer if needed,
        // sticking to online for simplicity as per previous patterns.
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Jugador no encontrado.", NamedTextColor.RED));
            return true;
        }

        if (target.equals(player)) {
            player.sendMessage(Component.text("No puedes ignorarte a ti mismo.", NamedTextColor.RED));
            return true;
        }

        if (target.hasPermission("vchat.bypass.ignore")) {
            player.sendMessage(Component.text("No puedes ignorar a este jugador.", NamedTextColor.RED));
            return true;
        }

        if (plugin.getIgnoreManager().isIgnored(player.getUniqueId(), target.getUniqueId())) {
            plugin.getIgnoreManager().removeIgnore(player, target.getUniqueId());
            player.sendActionBar(Component.text("Ya no ignoras a " + target.getName(), NamedTextColor.GREEN));
            playSound(player, "sounds.unignore");
            return true;
        }

        plugin.getIgnoreManager().addIgnore(player, target.getUniqueId());
        player.sendActionBar(Component.text("Ahora ignoras a " + target.getName(), NamedTextColor.RED));
        playSound(player, "sounds.ignore");

        return true;
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfigManager().getPrivate().getString(key);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
