package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class MuteChatCommand implements CommandExecutor {

    private final VChat plugin;

    public MuteChatCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("vchat.mutechat")) {
            plugin.getAdminManager().sendConfigMessage(sender, "messages.no-permission");
            return true;
        }

        plugin.getAdminManager().toggleGlobalChat();
        // Messages are handled in manager (broadcast)
        return true;
    }
}
