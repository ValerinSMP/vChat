package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
            player.sendMessage(MiniMessage.miniMessage().deserialize("<gradient:#d4af37:#f0e68c>SpyChat activado.</gradient>")); // Gold/Yellow ish
            player.sendActionBar(MiniMessage.miniMessage().deserialize("<gradient:#d4af37:#f0e68c>SpyChat activado.</gradient>"));
            playSound(player, "sounds.toggle-on");
        } else {
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>SpyChat desactivado.</red>"));
            player.sendActionBar(MiniMessage.miniMessage().deserialize("<red>SpyChat desactivado.</red>"));
            playSound(player, "sounds.toggle-off");
        }
    }

    public void sendPrivateMessage(Player sender, Player target, String message) {
        // Checks
        if (!isMsgEnabled(sender)) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-self");
            return;
        }
        if (!isMsgEnabled(target) && !sender.hasPermission("vchat.bypass.msg")) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.disabled-target"); // Need %player% replacement?
            return;
        }

        // Check if target ignores sender
        if (plugin.getIgnoreManager().isIgnored(target.getUniqueId(), sender.getUniqueId()) && !sender.hasPermission("vchat.bypass.ignore")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Este jugador te ha ignorado.</red>"));
            return;
        }

        // Reply Link
        lastRunners.put(target.getUniqueId(), sender.getUniqueId());
        lastRunners.put(sender.getUniqueId(), target.getUniqueId());

        // Format Components
        String outgoingFormat = plugin.getConfigManager().getPrivate().getString("outgoing");
        String incomingFormat = plugin.getConfigManager().getPrivate().getString("incoming");

        Component outgoing = formatData(outgoingFormat, sender, target, message);
        Component incoming = formatData(incomingFormat, sender, target, message);

        sender.sendMessage(outgoing);
        target.sendMessage(incoming);
        
        sender.sendActionBar(outgoing);
        target.sendActionBar(incoming);

        // Sounds
        playSound(sender, "sounds.message-send");
        playSound(target, "sounds.message-receive");

        // Social Spy
        notifySocialSpy(sender, target, message);
    }

    public void reply(Player sender, String message) {
        UUID targetId = lastRunners.get(sender.getUniqueId());
        if (targetId == null) {
            plugin.getAdminManager().sendConfigMessage(sender, "private.no-reply");
            return;
        }

        Player target = Bukkit.getPlayer(targetId);
        if (target == null || !target.isOnline()) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>El jugador ya no est\u00e1 en l\u00ednea.</red>"));
            return;
        }

        sendPrivateMessage(sender, target, message);
    }

    private Component formatData(String format, Player sender, Player target, String message) {
        return miniMessage.deserialize(format,
            Placeholder.component("sender", Component.text(sender.getName())),
            Placeholder.component("receiver", Component.text(target.getName())),
            Placeholder.component("message", Component.text(message))
        );
    }

    private void notifySocialSpy(Player sender, Player target, String message) {
        String format = plugin.getConfigManager().getPrivate().getString("spy-format", "<gradient:#D8BFD8:#FFB7C5>[Spy] <sender> -> <receiver>: <message></gradient>");
        
        Component spyComponent = formatData(format, sender, target, message);

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(sender) || online.equals(target)) continue;
            
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
