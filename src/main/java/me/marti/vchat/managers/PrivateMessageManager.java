package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import me.marti.vchat.redis.PendingAckRegistry;
import me.marti.vchat.redis.Presence;
import me.marti.vchat.redis.DeliveryStatus;
import me.marti.vchat.redis.RedisEvent;
import me.marti.vchat.redis.RedisEventType;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrivateMessageManager {
    private static final UUID CONSOLE_UUID = new UUID(0L, 0L);
    private final VChat plugin;
    private final org.bukkit.NamespacedKey msgToggleKey;
    private final org.bukkit.NamespacedKey spyToggleKey;
    private final Map<UUID, Boolean> msgToggleCache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> spyToggleCache = new ConcurrentHashMap<>();
    private final ReplyDirectory replies = new ReplyDirectory();
    private final PendingAckRegistry<PendingMessage> pendingAcks = new PendingAckRegistry<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PrivateMessageManager(VChat plugin) {
        this.plugin = plugin;
        this.msgToggleKey = new org.bukkit.NamespacedKey(plugin, "msg_enabled");
        this.spyToggleKey = new org.bukkit.NamespacedKey(plugin, "social_spy");
    }

    public void loadData(Player player) {
        Byte msg = player.getPersistentDataContainer().get(msgToggleKey, PersistentDataType.BYTE);
        Byte spy = player.getPersistentDataContainer().get(spyToggleKey, PersistentDataType.BYTE);
        msgToggleCache.put(player.getUniqueId(), msg == null || msg == 1);
        spyToggleCache.put(player.getUniqueId(), spy != null && spy == 1);
    }

    public void unloadData(Player player) {
        msgToggleCache.remove(player.getUniqueId());
        spyToggleCache.remove(player.getUniqueId());
    }

    public void applyState(Player player, boolean msgEnabled, boolean spyEnabled) {
        msgToggleCache.put(player.getUniqueId(), msgEnabled);
        spyToggleCache.put(player.getUniqueId(), spyEnabled);
        player.getPersistentDataContainer().set(msgToggleKey, PersistentDataType.BYTE, msgEnabled ? (byte) 1 : (byte) 0);
        player.getPersistentDataContainer().set(spyToggleKey, PersistentDataType.BYTE, spyEnabled ? (byte) 1 : (byte) 0);
    }

    public boolean isMsgEnabled(Player player) { return msgToggleCache.getOrDefault(player.getUniqueId(), true); }
    public boolean isSpyEnabled(Player player) { return spyToggleCache.getOrDefault(player.getUniqueId(), false); }

    public void toggleMsg(Player player) {
        boolean enabled = !isMsgEnabled(player);
        msgToggleCache.put(player.getUniqueId(), enabled);
        player.getPersistentDataContainer().set(msgToggleKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
        plugin.savePlayerState(player);
        plugin.getAdminManager().sendConfigActionBar(player, enabled ? "private.toggled-on" : "private.toggled-off");
        playSound(player, enabled ? "sounds.toggle-on" : "sounds.toggle-off");
    }

    public void toggleSpy(Player player) {
        boolean enabled = !isSpyEnabled(player);
        spyToggleCache.put(player.getUniqueId(), enabled);
        player.getPersistentDataContainer().set(spyToggleKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
        plugin.savePlayerState(player);
        plugin.getAdminManager().sendConfigActionBar(player, enabled ? "private.spy-enabled" : "private.spy-disabled");
        playSound(player, enabled ? "sounds.toggle-on" : "sounds.toggle-off");
    }

    public void sendPrivateMessage(CommandSender sender, Player target, String message) {
        UUID senderId = senderId(sender);
        if (!allowedLocal(sender, target)) return;
        Component body = parseMessage(sender, message);
        Component outgoing = format("outgoing", sender.getName(), target.getName(), body);
        Component incoming = format("incoming", sender.getName(), target.getName(), body);
        Component spy = format("spy-format", sender.getName(), target.getName(), body);
        PlatformUtil.sendMessage(sender, outgoing);
        PlatformUtil.sendMessage(target, incoming);
        plugin.getAdminManager().sendConfigActionBar(target, "private.new-message-notice", Placeholder.unparsed("player", sender.getName()));
        if (sender instanceof Player player) playSound(player, "sounds.message-send");
        playSound(target, "sounds.message-receive");
        link(senderId, sender.getName(), target.getUniqueId(), target.getName());
        notifyLocalSpies(senderId, target.getUniqueId(), spy);
        publishSpy(senderId, sender.getName(), target.getUniqueId(), target.getName(), spy);
    }

    public void sendByExactName(CommandSender sender, String targetName, String message) {
        if (plugin.getRedisManager() == null || !plugin.getRedisManager().isEnabled()) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.cross-server-unavailable");
            return;
        }
        plugin.getRedisManager().findPlayer(targetName, presence -> {
            if (presence == null) plugin.getAdminManager().sendConfigMessage(sender, "messages.player-not-found");
            else sendCrossServerMessage(sender, presence, message);
        });
    }

    public void reply(CommandSender sender, String message) {
        UUID senderId = senderId(sender);
        ReplyDirectory.Contact contact = replies.get(senderId);
        if (contact == null) { plugin.getAdminManager().sendConfigMessage(sender, "private.no-reply"); return; }
        if (contact.playerId().equals(CONSOLE_UUID)) { sendReplyToConsole(sender, message); return; }
        Player local = Bukkit.getPlayer(contact.playerId());
        if (local != null && local.isOnline()) { sendPrivateMessage(sender, local, message); return; }
        if (plugin.getRedisManager() == null || !plugin.getRedisManager().isEnabled()) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.cross-server-unavailable");
            return;
        }
        plugin.getRedisManager().findPlayer(contact.playerId(), presence -> {
            if (presence == null) plugin.getAdminManager().sendConfigMessage(sender, "messages.player-not-found");
            else sendCrossServerMessage(sender, presence, message);
        });
    }

    private void sendCrossServerMessage(CommandSender sender, Presence target, String message) {
        UUID senderId = senderId(sender);
        boolean bypassToggle = sender instanceof Player player && canBypassToggleMsg(player);
        boolean bypassIgnore = sender instanceof Player player && canBypassIgnore(player);
        if (sender instanceof Player player && !isMsgEnabled(player) && !bypassToggle) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-self");
            return;
        }
        plugin.getStorageManager().load(target.playerId()).thenAccept(state -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!state.msgEnabled() && !bypassToggle) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-target", Placeholder.unparsed("player", target.playerName()));
            } else if (state.ignores().contains(senderId) && !bypassIgnore) {
                plugin.getAdminManager().sendConfigMessage(sender, "private.ignored-you");
            } else {
                publishPrivateMessage(sender, target, message);
            }
        })).exceptionally(error -> {
            Bukkit.getScheduler().runTask(plugin, () -> plugin.getAdminManager().sendConfigMessage(sender, "private.cross-server-unavailable"));
            return null;
        });
    }

    private void publishPrivateMessage(CommandSender sender, Presence target, String message) {
        UUID senderId = senderId(sender);
        Component body = parseMessage(sender, message);
        Component outgoing = format("outgoing", sender.getName(), target.playerName(), body);
        Component incoming = format("incoming", sender.getName(), target.playerName(), body);
        Component spy = format("spy-format", sender.getName(), target.playerName(), body);
        RedisEvent event = new RedisEvent(RedisEventType.PRIVATE_MSG)
                .put("targetServer", target.serverId()).put("senderUuid", senderId.toString())
                .put("senderName", sender.getName()).put("targetUuid", target.playerId().toString())
                .put("targetName", target.playerName()).put("incoming", GsonComponentSerializer.gson().serialize(incoming))
                .put("spy", GsonComponentSerializer.gson().serialize(spy));
        pendingAcks.add(event.eventId, new PendingMessage(senderId, sender.getName(), target.playerId(), target.playerName(), outgoing));
        if (!plugin.getRedisManager().publish(event)) {
            pendingAcks.complete(event.eventId);
            plugin.getAdminManager().sendConfigMessage(sender, "private.cross-server-unavailable");
            return;
        }
        long timeoutTicks = Math.max(20L, plugin.getConfigManager().getMainConfig().getLong("redis.ack-timeout-millis", 3000L) / 50L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingMessage timedOut = pendingAcks.complete(event.eventId);
            if (timedOut != null) notifyFailure(timedOut);
        }, timeoutTicks);
    }

    public void deliverRemoteMessage(RedisEvent event) {
        if (!plugin.getRedisManager().getServerId().equals(event.payload.get("targetServer"))) return;
        UUID targetId = parseUuid(event.payload.get("targetUuid"));
        UUID senderId = parseUuid(event.payload.get("senderUuid"));
        if (targetId == null || senderId == null) return;
        Player target = Bukkit.getPlayer(targetId);
        DeliveryStatus status = DeliveryStatus.OFFLINE;
        if (target != null && target.isOnline()) {
            if (!isMsgEnabled(target) || plugin.getIgnoreManager().isIgnored(targetId, senderId)) {
                status = DeliveryStatus.BLOCKED;
            } else {
                try {
                    Component incoming = GsonComponentSerializer.gson().deserialize(event.payload.get("incoming"));
                    Component spy = GsonComponentSerializer.gson().deserialize(event.payload.get("spy"));
                    PlatformUtil.sendMessage(target, incoming);
                    plugin.getAdminManager().sendConfigActionBar(target, "private.new-message-notice", Placeholder.unparsed("player", event.payload.get("senderName")));
                    playSound(target, "sounds.message-receive");
                    replies.link(targetId, event.payload.get("targetName"), senderId, event.payload.get("senderName"));
                    notifyLocalSpies(senderId, targetId, spy);
                    publishSpy(senderId, event.payload.get("senderName"), targetId, event.payload.get("targetName"), spy);
                    status = DeliveryStatus.DELIVERED;
                } catch (RuntimeException invalidComponent) { status = DeliveryStatus.INVALID; }
            }
        }
        plugin.getRedisManager().publish(new RedisEvent(RedisEventType.PRIVATE_ACK)
                .put("targetServer", event.sourceServer).put("requestId", event.eventId).put("status", status.name()));
    }

    public void handleAck(RedisEvent event) {
        if (!plugin.getRedisManager().getServerId().equals(event.payload.get("targetServer"))) return;
        PendingMessage pending = pendingAcks.complete(event.payload.get("requestId"));
        if (pending == null) return;
        if (!DeliveryStatus.parse(event.payload.get("status")).delivered()) { notifyFailure(pending); return; }
        CommandSender sender = resolveSender(pending.senderId());
        if (sender == null) return;
        PlatformUtil.sendMessage(sender, pending.outgoing());
        if (sender instanceof Player player) playSound(player, "sounds.message-send");
        link(pending.senderId(), pending.senderName(), pending.targetId(), pending.targetName());
    }

    public void handleSocialSpy(RedisEvent event) {
        UUID senderId = parseUuid(event.payload.get("senderUuid"));
        UUID targetId = parseUuid(event.payload.get("targetUuid"));
        if (senderId == null || targetId == null) return;
        try { notifyLocalSpies(senderId, targetId, GsonComponentSerializer.gson().deserialize(event.payload.get("component"))); }
        catch (RuntimeException ignored) { }
    }

    private boolean allowedLocal(CommandSender sender, Player target) {
        if (!(sender instanceof Player player)) return true;
        if (!isMsgEnabled(player) && !canBypassToggleMsg(player)) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-self"); return false;
        }
        if (!isMsgEnabled(target) && !canBypassToggleMsg(player)) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-target", Placeholder.unparsed("player", target.getName())); return false;
        }
        if (plugin.getIgnoreManager().isIgnored(target.getUniqueId(), player.getUniqueId()) && !canBypassIgnore(player)) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.ignored-you"); return false;
        }
        return true;
    }

    private void publishSpy(UUID senderId, String senderName, UUID targetId, String targetName, Component component) {
        if (plugin.getRedisManager() == null || !plugin.getRedisManager().isEnabled()) return;
        plugin.getRedisManager().publish(new RedisEvent(RedisEventType.SOCIAL_SPY)
                .put("senderUuid", senderId.toString()).put("senderName", senderName)
                .put("targetUuid", targetId.toString()).put("targetName", targetName)
                .put("component", GsonComponentSerializer.gson().serialize(component)));
    }

    private void notifyLocalSpies(UUID senderId, UUID targetId, Component component) {
        for (Player spy : Bukkit.getOnlinePlayers()) {
            if (spy.getUniqueId().equals(senderId) || spy.getUniqueId().equals(targetId)) continue;
            if (spy.hasPermission("vchat.spychat") && isSpyEnabled(spy)) PlatformUtil.sendMessage(spy, component);
        }
    }

    private void link(UUID senderId, String senderName, UUID targetId, String targetName) {
        replies.link(senderId, senderName, targetId, targetName);
    }

    private void sendReplyToConsole(CommandSender sender, String message) {
        Component body = parseMessage(sender, message);
        PlatformUtil.sendMessage(sender, format("outgoing", sender.getName(), Bukkit.getConsoleSender().getName(), body));
        PlatformUtil.sendMessage(Bukkit.getConsoleSender(), format("incoming", sender.getName(), Bukkit.getConsoleSender().getName(), body));
    }

    private Component parseMessage(CommandSender sender, String message) {
        return MessageSanitizer.parse(sender, MessageSanitizer.prepare(sender, message), null);
    }

    private Component format(String path, String sender, String receiver, Component message) {
        String fallback = path.equals("spy-format") ? "<dark_gray>[Spy] <sender> -> <receiver>: <message>" : "<sender> -> <receiver>: <message>";
        return miniMessage.deserialize(plugin.getConfigManager().getPrivate().getString(path, fallback),
                Placeholder.unparsed("sender", sender), Placeholder.unparsed("receiver", receiver), Placeholder.component("message", message));
    }

    private void notifyFailure(PendingMessage pending) {
        CommandSender sender = resolveSender(pending.senderId());
        if (sender != null) plugin.getAdminManager().sendConfigMessage(sender, "private.delivery-failed");
    }

    private CommandSender resolveSender(UUID senderId) {
        if (CONSOLE_UUID.equals(senderId)) return Bukkit.getConsoleSender();
        Player player = Bukkit.getPlayer(senderId);
        return player != null && player.isOnline() ? player : null;
    }

    private static UUID senderId(CommandSender sender) { return sender instanceof Player player ? player.getUniqueId() : CONSOLE_UUID; }
    private static UUID parseUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException invalid) { return null; } }

    private void playSound(Player player, String key) {
        String soundName = plugin.getConfigManager().getPrivate().getString(key);
        Sound sound = soundName == null ? null : PlatformUtil.resolveSound(soundName);
        if (sound != null) player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    private boolean canBypassToggleMsg(Player player) {
        return player.hasPermission("vchat.bypass.msg") || player.hasPermission("vchat.bypass.togglemsg") || player.hasPermission("vchat.bypass.social");
    }

    private boolean canBypassIgnore(Player player) {
        return player.hasPermission("vchat.bypass.ignore") || player.hasPermission("vchat.bypass.social");
    }

    private record PendingMessage(UUID senderId, String senderName, UUID targetId, String targetName, Component outgoing) { }
}
