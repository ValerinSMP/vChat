package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import me.marti.vchat.utils.MessageSanitizer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PrivateMessageManager {

    private final VChat plugin;
    private final Map<UUID, UUID> lastRunners = new HashMap<>(); // Receiver -> Sender (for reply)
    private final org.bukkit.NamespacedKey msgToggleKey;
    private final org.bukkit.NamespacedKey spyToggleKey;
    private final Map<UUID, Boolean> msgToggleCache = new HashMap<>();
    private final Map<UUID, Boolean> spyToggleCache = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    private static final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public PrivateMessageManager(VChat plugin) {
        this.plugin = plugin;
        this.msgToggleKey = new org.bukkit.NamespacedKey(plugin, "msg_enabled");
        this.spyToggleKey = new org.bukkit.NamespacedKey(plugin, "social_spy");
    }

    public void loadData(Player player) {
        // Msg Toggle (Default true)
        boolean msgEnabled = true;
        if (player.getPersistentDataContainer().has(msgToggleKey, PersistentDataType.BYTE)) {
            msgEnabled = player.getPersistentDataContainer().get(msgToggleKey, PersistentDataType.BYTE) == 1;
        }
        msgToggleCache.put(player.getUniqueId(), msgEnabled);

        // Spy Toggle (Default false)
        boolean spyEnabled = false;
        if (player.getPersistentDataContainer().has(spyToggleKey, PersistentDataType.BYTE)) {
            spyEnabled = player.getPersistentDataContainer().get(spyToggleKey, PersistentDataType.BYTE) == 1;
        }
        spyToggleCache.put(player.getUniqueId(), spyEnabled);
    }

    public void unloadData(Player player) {
        msgToggleCache.remove(player.getUniqueId());
        spyToggleCache.remove(player.getUniqueId());
        lastRunners.remove(player.getUniqueId());
    }

    public boolean isMsgEnabled(Player player) {
        return msgToggleCache.getOrDefault(player.getUniqueId(), true);
    }

    public boolean isSpyEnabled(Player player) {
        return spyToggleCache.getOrDefault(player.getUniqueId(), false);
    }

    public void toggleMsg(Player player) {
        boolean newState = !isMsgEnabled(player);
        msgToggleCache.put(player.getUniqueId(), newState);
        player.getPersistentDataContainer().set(msgToggleKey, PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);

        if (newState) {
            plugin.getAdminManager().sendConfigActionBar(player, "private.toggled-on");
            playSound(player, "sounds.toggle-on");
        } else {
            plugin.getAdminManager().sendConfigActionBar(player, "private.toggled-off");
            playSound(player, "sounds.toggle-off");
        }
    }

    public void toggleSpy(Player player) {
        boolean newState = !isSpyEnabled(player);
        spyToggleCache.put(player.getUniqueId(), newState);
        player.getPersistentDataContainer().set(spyToggleKey, PersistentDataType.BYTE, newState ? (byte) 1 : (byte) 0);

        if (newState) {
            player.sendActionBar(
                    MiniMessage.miniMessage().deserialize("<gradient:#d4af37:#f0e68c>SpyChat activado.</gradient>")); // Gold/Yellow
                                                                                                                      // ish
            playSound(player, "sounds.toggle-on");
        } else {
            player.sendActionBar(MiniMessage.miniMessage().deserialize("<red>SpyChat desactivado.</red>"));
            playSound(player, "sounds.toggle-off");
        }
    }

    public void sendPrivateMessage(CommandSender sender, Player target, String message) {
        UUID senderUUID = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;

        // Checks (Only if sender is player)
        if (sender instanceof Player p) {
            if (!isMsgEnabled(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-self");
                return;
            }
            if (!isMsgEnabled(target) && !p.hasPermission("vchat.bypass.msg")) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-target");
                return;
            }
            // Check if target ignores sender
            if (plugin.getIgnoreManager().isIgnored(target.getUniqueId(), p.getUniqueId())
                    && !p.hasPermission("vchat.bypass.ignore")) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Este jugador te ha ignorado.</red>"));
                return;
            }
        }

        // Reply Link
        lastRunners.put(target.getUniqueId(), senderUUID);
        // If console, we can technically allow reply back to console
        if (senderUUID != CONSOLE_UUID) {
            lastRunners.put(senderUUID, target.getUniqueId());
        }

        // Format Components
        String outgoingFormat = plugin.getConfigManager().getPrivate().getString("outgoing");
        String incomingFormat = plugin.getConfigManager().getPrivate().getString("incoming");

        // Process Message Colors/Formats
        String prepared = MessageSanitizer.prepare(sender, message);
        Component messageComp = MessageSanitizer.parse(sender, prepared, null);

        Component outgoing = formatData(outgoingFormat, sender, target, messageComp);
        Component incoming = formatData(incomingFormat, sender, target, messageComp);

        sender.sendMessage(outgoing);
        target.sendMessage(incoming);

        // Premium Feedback: Notify target in ActionBar that they got a message
        target.sendActionBar(
                miniMessage.deserialize("<gray>✉ Nuevo mensaje de <white>" + sender.getName() + "</white></gray>"));

        // Sounds
        if (sender instanceof Player p) {
            playSound(p, "sounds.message-send");
        }
        playSound(target, "sounds.message-receive");

        // Social Spy
        notifySocialSpy(sender, target, messageComp);
    }

    public void reply(CommandSender sender, String message) {
        UUID senderUUID = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;

        UUID targetId = lastRunners.get(senderUUID);
        if (targetId == null) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.no-reply");
            return;
        }

        // Handle reply to console?
        // If targetId is CONSOLE_UUID, we can't use Bukkit.getPlayer(targetId).
        // Since we only set lastRunners for targets as senderUUID...
        // If I am CONSOLE, my target is a Player UUID.
        // If I am Player, my target *could* be CONSOLE_UUID if Console messaged me.

        if (targetId.equals(CONSOLE_UUID)) {
            // Reply to Console
            CommandSender targetConsole = Bukkit.getConsoleSender();
            // We reuse sendPrivateMessage logic, but sendPrivateMessage expects Player
            // target.
            // We need to handle Console as target too? Or just block reply to console for
            // now?
            // "permitir msg de consola". Doesn't explicitly ask for reply to console.
            // But it's nice. However, sendPrivateMessage signature is (CommandSender,
            // Player).
            // Let's keep it simple: If target is console, handle directly here or error.

            // For now, let's just say "Console" cannot be a target of /msg (standard MC
            // behavior usually).
            // But if I want to support reply to console, I need to refactor
            // sendPrivateMessage to accept CommandSender target.
            // Let's stick to user request: "permitir msg de consola".
            // So Console -> Player works. Player replying to Console... maybe validation
            // error "Cannot reply to console".
            sender.sendMessage(Component.text("No puedes responder a la consola (aún).", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(
                    MiniMessage.miniMessage().deserialize("<red>El jugador ya no est\u00e1 en l\u00ednea.</red>"));
            return;
        }

        sendPrivateMessage(sender, target, message);
    }

    private Component formatData(String format, CommandSender sender, Player target, Component message) {
        return miniMessage.deserialize(format,
                Placeholder.component("sender", Component.text(sender.getName())),
                Placeholder.component("receiver", Component.text(target.getName())),
                Placeholder.component("message", message));
    }

    private void notifySocialSpy(CommandSender sender, Player target, Component message) {
        String format = plugin.getConfigManager().getPrivate().getString("spy-format",
                "<gradient:#D8BFD8:#FFB7C5>[Spy] <sender> -> <receiver>: <message></gradient>");

        Component spyComponent = formatData(format, sender, target, message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender) || online.equals(target))
                continue;

            if (online.hasPermission("vchat.spychat") && isSpyEnabled(online)) {
                online.sendMessage(spyComponent);
            }
        }
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfigManager().getPrivate().getString(key);
        if (soundName != null && !soundName.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundName.toUpperCase());
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    // Send feedback via ActionBar and Chat
    private void sendFeedback(Player player, String messageKey) {
        // Implementation depends on where messages are stored.
        // If messageKey refers to messages.yml path:
        // plugin.getAdminManager().sendConfigMessage(player, messageKey);
        // AND send actionbar?
        // For this task, we can just ensure sounds are played.
    }
}
