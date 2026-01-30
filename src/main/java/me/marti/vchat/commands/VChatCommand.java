package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import me.marti.vchat.managers.ItemViewManager;
import me.marti.vchat.managers.AdminManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class VChatCommand implements CommandExecutor {

    private final VChat plugin;
    private final ItemViewManager itemViewManager;
    private final AdminManager adminManager;

    public VChatCommand(VChat plugin, ItemViewManager itemViewManager, AdminManager adminManager) {
        this.plugin = plugin;
        this.itemViewManager = itemViewManager;
        this.adminManager = adminManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (args.length == 0) {
            sender.sendMessage(Component.text("vChat: /vchat reload | /vchat notify", NamedTextColor.RED));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Reload Command
        if (subCommand.equals("reload")) {
            if (!sender.hasPermission("vchat.admin")) {
                adminManager.sendConfigMessage(sender, "messages.no-permission");
                return true;
            }
            plugin.reload();
            // Re-load filters too
            if (plugin.getFilterManager() != null)
                plugin.getFilterManager().loadFilters();

            adminManager.sendConfigMessage(sender, "messages.reload");

            // Play sound if player
            if (sender instanceof Player player) {
                adminManager.playSound(player, "sounds.reload");
            }
            return true;
        }

        // Help Command
        if (subCommand.equals("help")) {
            sender.sendMessage(Component.text("--- vChat Help ---", NamedTextColor.GOLD));
            if (sender.hasPermission("vchat.admin")) {
                sender.sendMessage(Component.text("/vchat reload - Recarga la configuración", NamedTextColor.YELLOW));
                sender.sendMessage(
                        Component.text("/vchat notify - Activa/Desactiva notificaciones", NamedTextColor.YELLOW));
            }
            // All players can see mention toggle
            sender.sendMessage(Component.text("/vchat mentions - Activa/Desactiva menciones", NamedTextColor.YELLOW));

            if (sender.hasPermission("vchat.showitem")) {
                sender.sendMessage(Component.text("/showitem - Muestra el ítem en mano", NamedTextColor.YELLOW));
            }
            return true;
        }

        // Mentions Toggle Command
        if (subCommand.equals("mentions")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Solo jugadores.");
                return true;
            }
            plugin.getMentionManager().toggleMentions(player);
            return true;
        }

        // Notify Toggle Command
        if (subCommand.equals("notify")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Solo jugadores.");
                return true;
            } else {
                if (!player.hasPermission("vchat.notify")) {
                    adminManager.sendConfigMessage(player, "messages.no-permission");
                    return true;
                }
                boolean newState = adminManager.toggleNotifications(player);
                if (newState) {
                    adminManager.sendConfigMessage(player, "messages.notify-enabled");
                    adminManager.playSound(player, "sounds.toggle-on");
                } else {
                    adminManager.sendConfigMessage(player, "messages.notify-disabled");
                    adminManager.playSound(player, "sounds.toggle-off");
                }
            }
            return true;
        }

        // View Item Command (Internal usually)
        if (subCommand.equals("viewitem")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
                return true;
            }
            // Permission check? Assuming default access if they have the link,
            // but we can add vchat.viewitem if strictness is needed. The plan said
            // Internal.

            if (args.length < 2) {
                player.sendMessage(Component.text("Invalid usage.", NamedTextColor.RED));
                return true;
            }

            try {
                UUID itemId = UUID.fromString(args[1]);
                itemViewManager.openView(player, itemId);
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("Invalid Item ID.", NamedTextColor.RED));
            }
            return true;
        }

        sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
        return true;
    }
}
