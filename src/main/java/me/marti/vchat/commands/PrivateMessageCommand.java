package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

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

        Player target = Bukkit.getPlayerExact(args[0]);
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        if (target == null || !target.isOnline()) {
            plugin.getPrivateMessageManager().sendByExactName(sender, args[0], message);
            return true;
        }

        if (sender instanceof Player player && target.equals(player)) {
            plugin.getAdminManager().sendConfigMessage(player, "private.no-self");
            return true;
        }

        plugin.getPrivateMessageManager().sendPrivateMessage(sender, target, message);
        return true;
    }
}
