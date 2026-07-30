package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import me.marti.vchat.managers.AdminManager;
import me.marti.vchat.managers.DiscordBridgeManager;
import me.marti.vchat.managers.ItemViewManager;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
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
        boolean adminRoot = command.getName().equalsIgnoreCase("vchatadmin");

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase().trim();

        switch (subCommand) {
            case "reload", "recargar" -> {
                if (!adminRoot) break;
                return handleReload(sender);
            }
            case "help", "ayuda" -> {
                sendHelp(sender);
                return true;
            }
            case "about", "info", "acerca" -> {
                sendAbout(sender);
                return true;
            }
            case "notify", "notificar", "notificaciones" -> {
                if (!adminRoot) break;
                return handleNotify(sender);
            }
            case "spy", "vspy", "spychat", "socialspy" -> {
                if (!adminRoot) break;
                return handleSpy(sender);
            }
            case "chat", "togglechat" -> {
                if (adminRoot) break;
                return handleChat(sender);
            }
            case "mentions", "menciones", "togglementions" -> {
                if (adminRoot) break;
                return handleMentions(sender);
            }
            case "msg", "msg_toggle", "togglemsg" -> {
                if (adminRoot) break;
                return handleMsgToggle(sender);
            }
            case "toggledeath", "tdeath" -> {
                if (adminRoot) break;
                return handleToggleDeath(sender);
            }
            case "viewitem" -> {
                if (adminRoot) break;
                return handleViewItem(sender, args);
            }
            case "bridge" -> {
                if (!adminRoot) break;
                return handleBridge(sender, args);
            }
            case "debug" -> {
                if (!adminRoot) break;
                return handleDebug(sender);
            }
        }

        adminManager.sendConfigMessage(sender, "messages.unknown-subcommand", Placeholder.unparsed("input", subCommand));
        return true;
    }

    /** True si sender tiene alguno de los permisos; si no, avisa y devuelve false. */
    private boolean requirePermission(CommandSender sender, String... perms) {
        for (String perm : perms) {
            if (sender.hasPermission(perm)) return true;
        }
        adminManager.sendConfigMessage(sender, "messages.no-permission");
        return false;
    }

    /** Devuelve el sender como Player o avisa "solo jugadores" y devuelve null. */
    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) return player;
        adminManager.sendConfigMessage(sender, "messages.players-only");
        return null;
    }

    private boolean handleReload(CommandSender sender) {
        if (!requirePermission(sender, "vchat.admin", "vchat.reload")) return true;
        plugin.reload();
        adminManager.sendConfigMessage(sender, "messages.reload");
        if (sender instanceof Player player) {
            adminManager.playSound(player, "sounds.reload");
        }
        return true;
    }

    private boolean handleNotify(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (!requirePermission(player, "vchat.notify", "vchat.admin")) return true;

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
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (!requirePermission(player, "vchat.spychat", "vchat.admin")) return true;

        plugin.getPrivateMessageManager().toggleSpy(player);
        return true;
    }

    private boolean handleChat(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (!requirePermission(player, "vchat.togglechat", "vchat.admin")) return true;

        plugin.getAdminManager().togglePersonalChat(player);
        return true;
    }

    private boolean handleMentions(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (!requirePermission(player, "vchat.togglementions", "vchat.admin")) return true;

        plugin.getMentionManager().toggleMentions(player);
        return true;
    }

    private boolean handleMsgToggle(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (!requirePermission(player, "vchat.togglemsg", "vchat.admin")) return true;

        plugin.getPrivateMessageManager().toggleMsg(player);
        return true;
    }

    private boolean handleToggleDeath(CommandSender sender) {
        Player player = requirePlayer(sender);
        if (player == null) return true;
        if (!requirePermission(player, "vchat.toggledeath", "vchat.admin")) return true;

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
        if (!(sender instanceof Player player)) return true;
        if (args.length < 2) return true;

        try {
            UUID itemId = UUID.fromString(args[1]);
            itemViewManager.openView(player, itemId);
        } catch (IllegalArgumentException e) {
            // Ignore malformed UUID
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        var messages = plugin.getConfigManager().getMessages();
        PlatformUtil.sendMini(sender, messages.getString("commands.help.header", ""));

        for (Map<?, ?> entry : messages.getMapList("commands.help.entries")) {
            Object permission = entry.get("permission");
            if (permission != null && !sender.hasPermission(permission.toString())) continue;
            PlatformUtil.sendMini(sender, String.valueOf(entry.get("line")));
        }

        PlatformUtil.sendMini(sender, messages.getString("commands.help.footer", ""));
    }

    private void sendAbout(CommandSender sender) {
        TagResolver version = Placeholder.unparsed("version", plugin.getDescription().getVersion());
        for (String line : plugin.getConfigManager().getMessages().getStringList("commands.about.lines")) {
            PlatformUtil.sendMini(sender, line, version);
        }
    }

    private boolean handleBridge(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "vchat.bridge.admin", "vchat.admin")) return true;

        if (args.length < 2) {
            adminManager.sendConfigMessage(sender, "bridge.usage");
            return true;
        }

        String action = args[1].toLowerCase();
        DiscordBridgeManager bridge = plugin.getDiscordBridgeManager();

        switch (action) {
            case "status" -> {
                adminManager.sendConfigMessage(sender, "bridge.status-header");
                adminManager.sendConfigMessage(sender, "bridge.status-enabled",
                        Placeholder.unparsed("enabled", String.valueOf(bridge.isEnabled())));
                adminManager.sendConfigMessage(sender, "bridge.status-server",
                        Placeholder.unparsed("server", String.valueOf(bridge.getServerId())));
                adminManager.sendConfigMessage(sender, "bridge.status-channel",
                        Placeholder.unparsed("channel", String.valueOf(bridge.getCurrentChannelId())));
                adminManager.sendConfigMessage(sender, "bridge.status-blocked",
                        Placeholder.unparsed("count", String.valueOf(bridge.getBlockedDiscordUsers().size())));
                return true;
            }
            case "reload" -> {
                bridge.reload();
                adminManager.sendConfigMessage(sender, "bridge.reloaded");
                return true;
            }
            case "block" -> {
                if (args.length < 3) {
                    adminManager.sendConfigMessage(sender, "bridge.block-usage");
                    return true;
                }
                boolean added = bridge.blockDiscordUser(args[2]);
                adminManager.sendConfigMessage(sender, added ? "bridge.blocked" : "bridge.already-blocked");
                return true;
            }
            case "unblock" -> {
                if (args.length < 3) {
                    adminManager.sendConfigMessage(sender, "bridge.unblock-usage");
                    return true;
                }
                boolean removed = bridge.unblockDiscordUser(args[2]);
                adminManager.sendConfigMessage(sender, removed ? "bridge.unblocked" : "bridge.not-blocked");
                return true;
            }
            case "list" -> {
                Set<String> blocked = bridge.getBlockedDiscordUsers();
                if (blocked.isEmpty()) {
                    adminManager.sendConfigMessage(sender, "bridge.list-empty");
                    return true;
                }
                adminManager.sendConfigMessage(sender, "bridge.list",
                        Placeholder.unparsed("list", String.join(", ", blocked)));
                return true;
            }
            case "test" -> {
                String msg = args.length >= 3 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : "bridge test";
                bridge.sendTestMessageToDiscord(msg);
                adminManager.sendConfigMessage(sender, "bridge.test-sent");
                return true;
            }
            default -> {
                adminManager.sendConfigMessage(sender, "bridge.unknown");
                return true;
            }
        }
    }

    private boolean handleDebug(CommandSender sender) {
        if (!requirePermission(sender, "vchat.admin", "vchat.debug")) return true;

        boolean enabled = plugin.toggleDebugMode();
        adminManager.sendConfigMessage(sender, enabled ? "debug.enabled" : "debug.disabled");
        return true;
    }
}
