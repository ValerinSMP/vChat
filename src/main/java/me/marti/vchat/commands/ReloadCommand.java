package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {

    private final VChat plugin;

    public ReloadCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("vchat.admin")) {
            PlatformUtil.sendMessage(sender,
                    Component.text("You do not have permission to execute this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            PlatformUtil.sendMessage(sender, Component.text("vChat configuration reloaded!", NamedTextColor.GREEN));
            return true;
        }

        PlatformUtil.sendMessage(sender, Component.text("Usage: /vchat reload", NamedTextColor.RED));
        return true;
    }
}
