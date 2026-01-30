package me.marti.vchat.commands;

import me.marti.vchat.managers.ItemViewManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ViewItemCommand implements CommandExecutor {

    private final ItemViewManager itemViewManager;

    public ViewItemCommand(ItemViewManager itemViewManager) {
        this.itemViewManager = itemViewManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            return false;
        }

        try {
            UUID itemId = UUID.fromString(args[0]);
            itemViewManager.openView(player, itemId);
        } catch (IllegalArgumentException e) {
            player.sendMessage(Component.text("Invalid Item ID.", NamedTextColor.RED));
        }

        return true;
    }
}
