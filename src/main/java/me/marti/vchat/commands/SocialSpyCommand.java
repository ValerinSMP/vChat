package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class SocialSpyCommand implements CommandExecutor {

    private final VChat plugin;

    public SocialSpyCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getAdminManager().sendConfigMessage(sender, "messages.players-only");
            return true;
        }

        if (!player.hasPermission("vchat.spychat") && !player.hasPermission("vchat.admin")) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.no-permission");
            return true;
        }

        plugin.getPrivateMessageManager().toggleSpy(player);
        return true;
    }
}
