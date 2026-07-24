package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
            plugin.getAdminManager().sendConfigMessage(sender, "messages.players-only");
            return true;
        }

        if (!player.hasPermission("vchat.ignore")) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.no-permission");
            return true;
        }

        if (args.length < 1) {
            plugin.getAdminManager().sendConfigMessage(player, "ignore.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.player-not-found");
            return true;
        }

        if (target.equals(player)) {
            plugin.getAdminManager().sendConfigMessage(player, "ignore.cant-ignore-self");
            return true;
        }

        if (target.hasPermission("vchat.bypass.ignore")) {
            plugin.getAdminManager().sendConfigMessage(player, "ignore.cant-ignore-target");
            return true;
        }

        var nameResolver = Placeholder.unparsed("player", target.getName());

        if (plugin.getIgnoreManager().isIgnored(player.getUniqueId(), target.getUniqueId())) {
            plugin.getIgnoreManager().removeIgnore(player, target.getUniqueId());
            plugin.getAdminManager().sendConfigActionBar(player, "ignore.no-longer-ignoring", nameResolver);
            playSound(player, "sounds.unignore");
            return true;
        }

        plugin.getIgnoreManager().addIgnore(player, target.getUniqueId());
        plugin.getAdminManager().sendConfigActionBar(player, "ignore.now-ignoring", nameResolver);
        playSound(player, "sounds.ignore");

        return true;
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfigManager().getPrivate().getString(key);
        if (soundName != null && !soundName.isEmpty()) {
            org.bukkit.Sound sound = me.marti.vchat.utils.PlatformUtil.resolveSound(soundName);
            if (sound != null) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }
}
