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

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            if (plugin.getRedisManager() != null && plugin.getRedisManager().isEnabled()) {
                plugin.getRedisManager().findPlayer(args[0], presence -> {
                    if (presence == null) plugin.getAdminManager().sendConfigMessage(player, "messages.player-not-found");
                    else toggle(player, presence.playerId(), presence.playerName());
                });
            } else {
                plugin.getAdminManager().sendConfigMessage(player, "messages.player-not-found");
            }
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

        toggle(player, target.getUniqueId(), target.getName());
        return true;
    }

    private void toggle(Player player, java.util.UUID targetId, String targetName) {
        var nameResolver = Placeholder.unparsed("player", targetName);

        if (plugin.getIgnoreManager().isIgnored(player.getUniqueId(), targetId)) {
            plugin.getIgnoreManager().removeIgnore(player, targetId);
            plugin.getAdminManager().sendConfigActionBar(player, "ignore.no-longer-ignoring", nameResolver);
            playSound(player, "sounds.unignore");
            return;
        }

        plugin.getIgnoreManager().addIgnore(player, targetId);
        plugin.getAdminManager().sendConfigActionBar(player, "ignore.now-ignoring", nameResolver);
        playSound(player, "sounds.ignore");
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfigManager().getPrivate().getString(key);
        if (soundName != null && !soundName.isEmpty()) {
            org.bukkit.Sound sound = me.marti.vchat.utils.PlatformUtil.resolveSound(soundName);
            if (sound != null) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }
}
