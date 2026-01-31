package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ToggleChatCommand implements CommandExecutor {

    private final VChat plugin;

    public ToggleChatCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
             sender.sendMessage("Consola no puede hacer esto.");
             return true;
        }
        
        if (!player.hasPermission("vchat.togglechat")) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.no-permission");
            return true;
        }

        plugin.getAdminManager().togglePersonalChat(player);
        return true;
    }
}
