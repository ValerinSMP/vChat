package me.marti.vchat.redis;

import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Aplica localmente los eventos recibidos por Redis de otros servers del cluster. */
public class RedisEventHandler {

    private final VChat plugin;

    public RedisEventHandler(VChat plugin) {
        this.plugin = plugin;
    }

    public void handle(RedisEvent event) {
        switch (event.type) {
            case CHAT -> handleChat(event);
            case PRIVATE_MSG -> handlePrivateMsg(event);
            case SOCIAL_SPY -> handleSocialSpy(event);
        }
    }

    private Component component(String json) {
        return GsonComponentSerializer.gson().deserialize(json);
    }

    private void handleChat(RedisEvent event) {
        Component message = component(event.componentJson);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (plugin.getAdminManager().isPersonalChatMuted(online)) continue;
            PlatformUtil.sendMessage(online, message);
        }
    }

    private void handlePrivateMsg(RedisEvent event) {
        if (event.targetUuid == null) return;
        Player target = Bukkit.getPlayer(UUID.fromString(event.targetUuid));
        if (target == null) return;

        Component incoming = component(event.componentJson);
        PlatformUtil.sendMessage(target, incoming);
        plugin.getAdminManager().sendConfigActionBar(target, "private.new-message-notice",
                Placeholder.unparsed("player", event.senderName));

        plugin.getPrivateMessageManager().registerRemoteReplyLink(target.getUniqueId(), event.senderUuid, event.senderName);
    }

    private void handleSocialSpy(RedisEvent event) {
        Component spyLine = component(event.componentJson);
        for (Player spy : Bukkit.getOnlinePlayers()) {
            if (spy.hasPermission("vchat.spychat") && plugin.getPrivateMessageManager().isSpyEnabled(spy)) {
                PlatformUtil.sendMessage(spy, spyLine);
            }
        }
    }
}
