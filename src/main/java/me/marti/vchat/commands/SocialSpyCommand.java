package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
            sender.sendMessage(Component.text("Solo jugadores.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("vchat.spychat")) {
            plugin.getAdminManager().sendConfigMessage(player, "messages.no-permission");
            return true;
        }

        plugin.getPrivateMessageManager().toggleSpy(player);
        return true;
    }
}
