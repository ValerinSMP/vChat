package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import me.marti.vchat.managers.ItemViewManager;
import me.marti.vchat.managers.AdminManager;
import me.marti.vchat.utils.PlatformUtil;
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
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase().trim();

        switch (subCommand) {
            case "reload":
            case "recargar":
                return handleReload(sender);
            case "help":
            case "ayuda":
                sendHelp(sender);
                return true;
            case "notify":
            case "notificar":
            case "notificaciones":
                return handleNotify(sender);
            case "spy":
            case "vspy":
            case "spychat":
            case "socialspy":
                return handleSpy(sender);
            case "chat":
            case "togglechat":
                return handleChat(sender);
            case "mentions":
            case "menciones":
            case "togglementions":
                return handleMentions(sender);
            case "msg":
            case "msg_toggle":
            case "togglemsg":
                return handleMsgToggle(sender);
            case "toggledeath":
            case "tdeath":
                return handleToggleDeath(sender);
            case "viewitem":
                return handleViewItem(sender, args);
            case "bridge":
                return handleBridge(sender, args);
            case "debug":
                return handleDebug(sender);
        }

        PlatformUtil.sendMessage(sender,
                Component.text("Subcomando desconocido: '" + subCommand + "'. Usa /vchat help.", NamedTextColor.RED));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("vchat.admin") && !sender.hasPermission("vchat.reload")) {
            adminManager.sendConfigMessage(sender, "messages.no-permission");
            return true;
        }
        plugin.reload();
        adminManager.sendConfigMessage(sender, "messages.reload");
        if (sender instanceof Player player) {
            adminManager.playSound(player, "sounds.reload");
        }
        return true;
    }

    private boolean handleNotify(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (!player.hasPermission("vchat.notify") && !player.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(player, "messages.no-permission");
            return true;
        }
        boolean newState = adminManager.toggleNotifications(player);
        if (newState) {
            adminManager.sendConfigActionBar(player, "messages.notify-enabled");
            adminManager.playSound(player, "sounds.toggle-on");
        } else {
            adminManager.sendConfigActionBar(player, "messages.notify-disabled");
            adminManager.playSound(player, "sounds.toggle-off");
        }
        return true;
    }

    private boolean handleSpy(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (!player.hasPermission("vchat.spychat") && !player.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(player, "messages.no-permission");
            return true;
        }
        plugin.getPrivateMessageManager().toggleSpy(player);
        return true;
    }

    private boolean handleChat(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (!player.hasPermission("vchat.togglechat") && !player.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(player, "messages.no-permission");
            return true;
        }
        plugin.getAdminManager().togglePersonalChat(player);
        return true;
    }

    private boolean handleMentions(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (!player.hasPermission("vchat.togglementions") && !player.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(player, "messages.no-permission");
            return true;
        }
        plugin.getMentionManager().toggleMentions(player);
        return true;
    }

    private boolean handleMsgToggle(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (!player.hasPermission("vchat.togglemsg") && !player.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(player, "messages.no-permission");
            return true;
        }
        plugin.getPrivateMessageManager().toggleMsg(player);
        return true;
    }

    private boolean handleToggleDeath(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores.");
            return true;
        }
        if (!player.hasPermission("vchat.toggledeath") && !player.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(player, "messages.no-permission");
            return true;
        }
        boolean muted = adminManager.toggleDeath(player);
        if (muted) {
            adminManager.sendConfigActionBar(player, "messages.death-disabled");
            adminManager.playSound(player, "sounds.toggle-off");
        } else {
            adminManager.sendConfigActionBar(player, "messages.death-enabled");
            adminManager.playSound(player, "sounds.toggle-on");
        }
        return true;
    }

    private boolean handleViewItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        if (args.length < 2) return true;

        // GUI disabled on Bukkit/Arclight — no Paper inventory Component API
        if (!me.marti.vchat.utils.PlatformUtil.isPaper()) return true;

        try {
            UUID itemId = UUID.fromString(args[1]);
            itemViewManager.openView(player, itemId);
        } catch (IllegalArgumentException e) {
            // Ignore malformed UUID
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        PlatformUtil.sendMessage(sender, Component.text(" ", NamedTextColor.GRAY));
        PlatformUtil.sendMessage(sender, net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(" <gradient:#50ffc5:#009985>vChat Help</gradient> "));
        PlatformUtil.sendMessage(sender, Component.text(" ", NamedTextColor.GRAY));

        // Basic Commands
        PlatformUtil.sendMessage(sender, formatCommand("/msg <player> <msg>", "Enviar mensaje privado"));
        PlatformUtil.sendMessage(sender, formatCommand("/reply <msg>", "Responder mensaje privado"));
        PlatformUtil.sendMessage(sender, formatCommand("/ignore <player>", "Ignorar a un jugador"));
        PlatformUtil.sendMessage(sender, formatCommand("/showitem", "Mostrar ítem en mano"));

        // Toggles
        PlatformUtil.sendMessage(sender, formatCommand("/togglechat", "Ocultar/Mostrar chat global"));
        PlatformUtil.sendMessage(sender, formatCommand("/togglementions", "Activar/Desactivar menciones"));
        PlatformUtil.sendMessage(sender, formatCommand("/togglemsg", "Activar/Desactivar mensajes privados"));

        // Admin / Staff
        if (sender.hasPermission("vchat.spychat") || sender.hasPermission("vchat.admin")) {
            PlatformUtil.sendMessage(sender, formatCommand("/vspy", "Espiar mensajes privados (SocialSpy)"));
        }
        if (sender.hasPermission("vchat.mutechat")) {
            PlatformUtil.sendMessage(sender, formatCommand("/mutechat", "Silenciar chat global (Todos)"));
        }
        if (sender.hasPermission("vchat.admin") || sender.hasPermission("vchat.reload")) {
            PlatformUtil.sendMessage(sender, formatCommand("/vchat reload", "Recargar configuración"));
            PlatformUtil.sendMessage(sender, formatCommand("/vchat debug", "Activar/Desactivar debug de parseo"));
        }
        if (sender.hasPermission("vchat.notify")) {
            PlatformUtil.sendMessage(sender, formatCommand("/vchat notify", "Notificaciones de admin"));
        }
        if (sender.hasPermission("vchat.bridge.admin") || sender.hasPermission("vchat.admin")) {
            PlatformUtil.sendMessage(sender, formatCommand("/vchat bridge status", "Estado del bridge de Discord"));
            PlatformUtil.sendMessage(sender, formatCommand("/vchat bridge block <id>", "Bloquear usuario de Discord"));
            PlatformUtil.sendMessage(sender, formatCommand("/vchat bridge unblock <id>", "Desbloquear usuario de Discord"));
            PlatformUtil.sendMessage(sender, formatCommand("/vchat bridge reload", "Recargar bridge de Discord"));
            PlatformUtil.sendMessage(sender, formatCommand("/vchat bridge test <msg>", "Enviar test por webhook"));
        }

        PlatformUtil.sendMessage(sender, Component.text(" ", NamedTextColor.GRAY));
    }

    private boolean handleBridge(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vchat.bridge.admin") && !sender.hasPermission("vchat.admin")) {
            adminManager.sendConfigMessage(sender, "messages.no-permission");
            return true;
        }

        if (args.length < 2) {
            PlatformUtil.sendMessage(sender, Component.text("Uso: /vchat bridge <status|reload|block|unblock|list|test>", NamedTextColor.RED));
            return true;
        }

        String action = args[1].toLowerCase();
        me.marti.vchat.managers.DiscordBridgeManager bridge = plugin.getDiscordBridgeManager();

        switch (action) {
            case "status" -> {
                PlatformUtil.sendMessage(sender, Component.text("Bridge enabled: " + bridge.isEnabled(), NamedTextColor.AQUA));
                PlatformUtil.sendMessage(sender, Component.text("Server ID: " + bridge.getServerId(), NamedTextColor.GRAY));
                PlatformUtil.sendMessage(sender, Component.text("Channel ID: " + bridge.getCurrentChannelId(), NamedTextColor.GRAY));
                PlatformUtil.sendMessage(sender, Component.text("Blocked users: " + bridge.getBlockedDiscordUsers().size(), NamedTextColor.GRAY));
                return true;
            }
            case "reload" -> {
                bridge.reload();
                PlatformUtil.sendMessage(sender, Component.text("Discord bridge recargado.", NamedTextColor.GREEN));
                return true;
            }
            case "block" -> {
                if (args.length < 3) {
                    PlatformUtil.sendMessage(sender, Component.text("Uso: /vchat bridge block <discordUserId>", NamedTextColor.RED));
                    return true;
                }
                boolean added = bridge.blockDiscordUser(args[2]);
                PlatformUtil.sendMessage(sender, Component.text(added ? "Usuario bloqueado." : "Ese usuario ya estaba bloqueado.",
                        added ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return true;
            }
            case "unblock" -> {
                if (args.length < 3) {
                    PlatformUtil.sendMessage(sender, Component.text("Uso: /vchat bridge unblock <discordUserId>", NamedTextColor.RED));
                    return true;
                }
                boolean removed = bridge.unblockDiscordUser(args[2]);
                PlatformUtil.sendMessage(sender, Component.text(removed ? "Usuario desbloqueado." : "Ese usuario no estaba bloqueado.",
                        removed ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
                return true;
            }
            case "list" -> {
                java.util.Set<String> blocked = bridge.getBlockedDiscordUsers();
                if (blocked.isEmpty()) {
                    PlatformUtil.sendMessage(sender, Component.text("No hay usuarios bloqueados.", NamedTextColor.GRAY));
                    return true;
                }
                PlatformUtil.sendMessage(sender, Component.text("Usuarios bloqueados: " + String.join(", ", blocked), NamedTextColor.YELLOW));
                return true;
            }
            case "test" -> {
                String msg = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length))
                        : "bridge test";
                bridge.sendTestMessageToDiscord(msg);
                PlatformUtil.sendMessage(sender, Component.text("Test enviado a Discord (webhook).", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                PlatformUtil.sendMessage(sender, Component.text("Subcomando bridge desconocido.", NamedTextColor.RED));
                return true;
            }
        }
    }

    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("vchat.admin") && !sender.hasPermission("vchat.debug")) {
            adminManager.sendConfigMessage(sender, "messages.no-permission");
            return true;
        }

        boolean enabled = plugin.toggleDebugMode();
        PlatformUtil.sendMessage(sender, Component.text("Debug vChat: " + (enabled ? "ACTIVADO" : "DESACTIVADO"),
                enabled ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        return true;
    }

    private Component formatCommand(String command, String description) {
        return net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                "<click:suggest_command:'" + command + "'><hover:show_text:'<gray>Click para escribir</gray>'><#50b5e8>"
                        + command + "</#50b5e8> <dark_gray>-</dark_gray> <gray>" + description
                        + "</gray></hover></click>");
    }
}
