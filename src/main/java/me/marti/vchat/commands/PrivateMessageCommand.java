package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public class PrivateMessageCommand implements CommandExecutor {

    private final VChat plugin;

    public PrivateMessageCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (sender instanceof Player player && !player.hasPermission("vchat.msg")) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.no-permission");
            return true;
        }

        if (args.length < 2) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.usage");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) { // isOnline check just in case getPlayer returns offline player (it
                                                    // shouldn't usually but safest)
            var redis = plugin.getRedisManager();
            if (redis != null && redis.isEnabled()) {
                String[] remote = redis.findRemotePlayer(args[0]);
                if (remote != null) {
                    String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    plugin.getPrivateMessageManager().sendCrossServerMessage(sender, args[0], UUID.fromString(remote[1]), message);
                    return true;
                }
            }
            plugin.getAdminManager().sendConfigMessage(sender, "messages.player-not-found");
            return true;
        }

        if (sender instanceof Player player && target.equals(player)) {
            plugin.getAdminManager().sendConfigMessage(player, "private.no-self");
            return true;
        }

        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getPrivateMessageManager().sendPrivateMessage(sender, target, message);
        return true;
    }
}
