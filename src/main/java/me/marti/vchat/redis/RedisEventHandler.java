package me.marti.vchat.redis;

import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Applies validated remote events. Bukkit access always converges on the main thread. */
public final class RedisEventHandler {
    private final VChat plugin;

    public RedisEventHandler(VChat plugin) {
        this.plugin = plugin;
    }

    public void handle(RedisEvent event) {
        if (!Bukkit.isPrimaryThread()) {
            me.marti.vchat.utils.MainThreadGate.dispatch(false, () -> handle(event),
                    task -> Bukkit.getScheduler().runTask(plugin, task));
            return;
        }
        switch (event.type) {
            case CHAT -> handleChat(event);
            case PRIVATE_MSG -> plugin.getPrivateMessageManager().deliverRemoteMessage(event);
            case PRIVATE_ACK -> plugin.getPrivateMessageManager().handleAck(event);
            case SOCIAL_SPY -> plugin.getPrivateMessageManager().handleSocialSpy(event);
            case JOIN, QUIT -> broadcastComponent(event.payload.get("component"));
            case GLOBAL_MUTE_INVALIDATE -> plugin.getAdminManager().refreshGlobalMute();
            case PREFERENCE_INVALIDATE -> reloadPreference(event.payload.get("playerUuid"));
        }
    }

    private void handleChat(RedisEvent event) {
        UUID senderId = parseUuid(event.payload.get("senderUuid"));
        Component component = deserialize(event.payload.get("component"));
        boolean bypassIgnore = Boolean.parseBoolean(event.payload.get("bypassIgnore"));
        if (senderId == null || component == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getAdminManager().isPersonalChatMuted(online)) continue;
            if (!bypassIgnore && plugin.getIgnoreManager().isIgnored(online.getUniqueId(), senderId)) continue;
            PlatformUtil.sendMessage(online, component);
        }
        PlatformUtil.sendMessage(Bukkit.getConsoleSender(), component);
    }

    private void broadcastComponent(String json) {
        Component component = deserialize(json);
        if (component == null) return;
        for (Player online : Bukkit.getOnlinePlayers()) PlatformUtil.sendMessage(online, component);
        PlatformUtil.sendMessage(Bukkit.getConsoleSender(), component);
    }

    private void reloadPreference(String rawUuid) {
        UUID uuid = parseUuid(rawUuid);
        if (uuid != null) plugin.reloadPlayerState(uuid);
    }

    private static Component deserialize(String json) {
        try { return GsonComponentSerializer.gson().deserialize(json); }
        catch (RuntimeException invalid) { return null; }
    }

    private static UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (RuntimeException invalid) { return null; }
    }
}
