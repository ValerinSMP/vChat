package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import me.marti.vchat.redis.RedisEvent;
import me.marti.vchat.redis.RedisEventType;
import me.marti.vchat.redis.RedisManager;
import me.marti.vchat.utils.MessageSanitizer;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PrivateMessageManager {

    private final VChat plugin;
    private final Map<UUID, UUID> lastRunners = new ConcurrentHashMap<>(); // Receiver -> Sender (for reply)
    // Solo poblado cuando el último "runner" está en otro server del cluster (reply cross-server).
    private final Map<UUID, String> lastRemoteNames = new ConcurrentHashMap<>();
    private final org.bukkit.NamespacedKey msgToggleKey;
    private final org.bukkit.NamespacedKey spyToggleKey;
    private final Map<UUID, Boolean> msgToggleCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> spyToggleCache = new ConcurrentHashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

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
        lastRemoteNames.remove(player.getUniqueId());
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
        redis().ifPresent(r -> r.setMsgToggle(player.getUniqueId(), newState));

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
            plugin.getAdminManager().sendConfigActionBar(player, "private.spy-enabled");
            playSound(player, "sounds.toggle-on");
        } else {
            plugin.getAdminManager().sendConfigActionBar(player, "private.spy-disabled");
            playSound(player, "sounds.toggle-off");
        }
    }

    public void sendPrivateMessage(CommandSender sender, Player target, String message) {
        UUID senderUUID = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;

        // Checks (Only if sender is player)
        if (sender instanceof Player p) {
            if (!isMsgEnabled(p) && !canBypassToggleMsg(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-self");
                return;
            }
            if (!isMsgEnabled(target) && !canBypassToggleMsg(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-target",
                        Placeholder.unparsed("player", target.getName()));
                return;
            }
            // Check if target ignores sender
            if (plugin.getIgnoreManager().isIgnored(target.getUniqueId(), p.getUniqueId())
                    && !canBypassIgnore(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.ignored-you");
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

        PlatformUtil.sendMessage(sender, outgoing);
        PlatformUtil.sendMessage(target, incoming);

        // Premium Feedback: Notify target in ActionBar that they got a message
        plugin.getAdminManager().sendConfigActionBar(target, "private.new-message-notice",
                Placeholder.unparsed("player", sender.getName()));

        // Sounds
        if (sender instanceof Player p) {
            playSound(p, "sounds.message-send");
        }
        playSound(target, "sounds.message-receive");

        // Social Spy
        notifySocialSpy(sender, target, messageComp);
        publishSpyEvent(sender.getName(), target.getName(), formatData(
                plugin.getConfigManager().getPrivate().getString("spy-format",
                        "<gradient:#D8BFD8:#FFB7C5>[Spy] <sender> -> <receiver>: <message></gradient>"),
                sender, target, messageComp));
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
            sendReplyToConsole(sender, message);
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && target.isOnline()) {
            sendPrivateMessage(sender, target, message);
            return;
        }

        // No est\u00e1 en este server \u2014 reintentar cross-server si sabemos su nombre.
        String remoteName = lastRemoteNames.get(senderUUID);
        if (remoteName != null) {
            String[] remote = redis().map(r -> r.findRemotePlayer(remoteName)).orElse(null);
            if (remote != null) {
                sendCrossServerMessage(sender, remoteName, UUID.fromString(remote[1]), message);
                return;
            }
        }

        PlatformUtil.sendMessage(sender,
                MiniMessage.miniMessage().deserialize("<red>El jugador ya no est\u00e1 en l\u00ednea.</red>"));
    }

    /** Enruta un /msg hacia un jugador online en otro server del cluster. */
    public void sendCrossServerMessage(CommandSender sender, String targetName, UUID targetUuid, String message) {
        RedisManager redis = plugin.getRedisManager();
        if (redis == null || !redis.isEnabled()) return;
        UUID senderUUID = (sender instanceof Player p) ? p.getUniqueId() : CONSOLE_UUID;

        if (sender instanceof Player p) {
            if (!isMsgEnabled(p) && !canBypassToggleMsg(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-self");
                return;
            }
            Boolean remoteToggle = redis.getRemoteMsgToggle(targetUuid);
            if (Boolean.FALSE.equals(remoteToggle) && !canBypassToggleMsg(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-target",
                        Placeholder.unparsed("player", targetName));
                return;
            }
            if (redis.isIgnoredRemote(targetUuid, p.getUniqueId()) && !canBypassIgnore(p)) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.ignored-you");
                return;
            }
        }

        lastRunners.put(targetUuid, senderUUID);
        lastRemoteNames.put(targetUuid, sender.getName());
        if (senderUUID != CONSOLE_UUID) {
            lastRunners.put(senderUUID, targetUuid);
            lastRemoteNames.put(senderUUID, targetName);
        }

        String outgoingFormat = plugin.getConfigManager().getPrivate().getString("outgoing");
        String incomingFormat = plugin.getConfigManager().getPrivate().getString("incoming");
        String spyFormat = plugin.getConfigManager().getPrivate().getString("spy-format",
                "<gradient:#D8BFD8:#FFB7C5>[Spy] <sender> -> <receiver>: <message></gradient>");

        String prepared = MessageSanitizer.prepare(sender, message);
        Component messageComp = MessageSanitizer.parse(sender, prepared, null);

        Component outgoing = miniMessage.deserialize(outgoingFormat,
                Placeholder.component("sender", Component.text(sender.getName())),
                Placeholder.component("receiver", Component.text(targetName)),
                Placeholder.component("message", messageComp));
        Component incoming = miniMessage.deserialize(incomingFormat,
                Placeholder.component("sender", Component.text(sender.getName())),
                Placeholder.component("receiver", Component.text(targetName)),
                Placeholder.component("message", messageComp));
        Component spyLine = miniMessage.deserialize(spyFormat,
                Placeholder.component("sender", Component.text(sender.getName())),
                Placeholder.component("receiver", Component.text(targetName)),
                Placeholder.component("message", messageComp));

        PlatformUtil.sendMessage(sender, outgoing);
        if (sender instanceof Player p) playSound(p, "sounds.message-send");

        RedisEvent msgEvent = new RedisEvent();
        msgEvent.type = RedisEventType.PRIVATE_MSG;
        msgEvent.senderName = sender.getName();
        msgEvent.senderUuid = senderUUID.toString();
        msgEvent.targetName = targetName;
        msgEvent.targetUuid = targetUuid.toString();
        msgEvent.componentJson = GsonComponentSerializer.gson().serialize(incoming);
        redis.publish(msgEvent);

        publishSpyEvent(sender.getName(), targetName, spyLine);
    }

    /** Registra el link de reply cuando un mensaje entrante viene de otro server. */
    public void registerRemoteReplyLink(UUID localPlayer, String remoteSenderUuid, String remoteSenderName) {
        if (remoteSenderUuid == null) return;
        lastRunners.put(localPlayer, UUID.fromString(remoteSenderUuid));
        lastRemoteNames.put(localPlayer, remoteSenderName);
    }

    private void publishSpyEvent(String senderName, String targetName, Component spyLine) {
        redis().filter(RedisManager::isEnabled).ifPresent(r -> {
            RedisEvent spyEvent = new RedisEvent();
            spyEvent.type = RedisEventType.SOCIAL_SPY;
            spyEvent.senderName = senderName;
            spyEvent.targetName = targetName;
            spyEvent.componentJson = GsonComponentSerializer.gson().serialize(spyLine);
            r.publish(spyEvent);
        });
    }

    private Optional<RedisManager> redis() {
        return Optional.ofNullable(plugin.getRedisManager());
    }

    private void sendReplyToConsole(CommandSender sender, String message) {
        CommandSender targetConsole = Bukkit.getConsoleSender();

        String outgoingFormat = plugin.getConfigManager().getPrivate().getString("outgoing");
        String incomingFormat = plugin.getConfigManager().getPrivate().getString("incoming");

        String prepared = MessageSanitizer.prepare(sender, message);
        Component messageComp = MessageSanitizer.parse(sender, prepared, null);

        Component outgoing = formatData(outgoingFormat, sender, targetConsole, messageComp);
        Component incoming = formatData(incomingFormat, sender, targetConsole, messageComp);

        PlatformUtil.sendMessage(sender, outgoing);
        PlatformUtil.sendMessage(targetConsole, incoming);

        if (sender instanceof Player playerSender) {
            playSound(playerSender, "sounds.message-send");
        }

        notifySocialSpy(sender, targetConsole, messageComp);
    }

    private Component formatData(String format, CommandSender sender, CommandSender target, Component message) {
        return miniMessage.deserialize(format,
                Placeholder.component("sender", Component.text(sender.getName())),
                Placeholder.component("receiver", Component.text(target.getName())),
                Placeholder.component("message", message));
    }

    private void notifySocialSpy(CommandSender sender, CommandSender target, Component message) {
        String format = plugin.getConfigManager().getPrivate().getString("spy-format",
                "<gradient:#D8BFD8:#FFB7C5>[Spy] <sender> -> <receiver>: <message></gradient>");

        Component spyComponent = formatData(format, sender, target, message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender) || online.equals(target))
                continue;

            if (online.hasPermission("vchat.spychat") && isSpyEnabled(online)) {
                PlatformUtil.sendMessage(online, spyComponent);
            }
        }
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfigManager().getPrivate().getString(key);
        if (soundName != null && !soundName.isEmpty()) {
            Sound sound = me.marti.vchat.utils.PlatformUtil.resolveSound(soundName);
            if (sound != null) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private boolean canBypassToggleMsg(Player player) {
        return player.hasPermission("vchat.bypass.msg")
                || player.hasPermission("vchat.bypass.togglemsg")
                || player.hasPermission("vchat.bypass.social");
    }

    private boolean canBypassIgnore(Player player) {
        return player.hasPermission("vchat.bypass.ignore")
                || player.hasPermission("vchat.bypass.social");
    }

}
