package me.marti.vchat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class PlatformUtil {

    private static final boolean PAPER_ACTION_BAR;
    private static final boolean PAPER_SENDER_COMPONENT;
    private static com.comphenix.protocol.ProtocolManager protocolManager;

    static {
        boolean actionBar = false;
        boolean senderComp = false;
        try {
            Player.class.getMethod("sendActionBar", Component.class);
            actionBar = true;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            CommandSender.class.getMethod("sendMessage", Component.class);
            senderComp = true;
        } catch (NoSuchMethodException ignored) {
        }
        PAPER_ACTION_BAR = actionBar;
        PAPER_SENDER_COMPONENT = senderComp;
    }

    private PlatformUtil() {
    }

    public static boolean isPaper() {
        return PAPER_SENDER_COMPONENT;
    }

    public static void initProtocolLib(Plugin plugin) {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            protocolManager = com.comphenix.protocol.ProtocolLibrary.getProtocolManager();
        }
    }

    public static void sendActionBar(Player player, Component component) {
        if (PAPER_ACTION_BAR) {
            player.sendActionBar(component);
            return;
        }
        // Arclight + ProtocolLib: send SET_ACTION_BAR_TEXT packet
        if (protocolManager != null) {
            try {
                com.comphenix.protocol.events.PacketContainer packet = protocolManager
                        .createPacket(com.comphenix.protocol.PacketType.Play.Server.SET_ACTION_BAR_TEXT);
                // The packet holds a WrappedChatComponent; serialize component to JSON
                String json = GsonComponentSerializer.gson().serialize(component);
                packet.getChatComponents().write(0,
                        com.comphenix.protocol.wrappers.WrappedChatComponent.fromJson(json));
                protocolManager.sendServerPacket(player, packet);
                return;
            } catch (Exception ignored) {
            }
        }
        // Last resort: chat message fallback
        player.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
    }

    public static void sendMessage(CommandSender sender, Component component) {
        if (PAPER_SENDER_COMPONENT) {
            sender.sendMessage(component);
            return;
        }
        // Arclight + ProtocolLib: send SYSTEM_CHAT packet with full JSON so that
        // gradients and RGB hex colors are preserved (legacy serializer drops them).
        if (protocolManager != null && sender instanceof Player player) {
            try {
                com.comphenix.protocol.events.PacketContainer packet = protocolManager
                        .createPacket(com.comphenix.protocol.PacketType.Play.Server.SYSTEM_CHAT);
                String json = GsonComponentSerializer.gson().serialize(component);
                packet.getChatComponents().write(0,
                        com.comphenix.protocol.wrappers.WrappedChatComponent.fromJson(json));
                // overlay = false (chat, not action bar)
                packet.getBooleans().write(0, false);
                protocolManager.sendServerPacket(player, packet);
                return;
            } catch (Exception ignored) {
            }
        }
        // Fallback for console or when ProtocolLib unavailable
        sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
    }

    /**
     * Resolves a Sound by name. Never calls Sound.valueOf() directly because Paper 1.21.4
     * made Sound an interface, generating InterfaceMethodref bytecode that Arclight rejects
     * even inside a catch block. Uses Registry first, then reflection-based valueOf fallback.
     */
    public static Sound resolveSound(String name) {
        if (name == null || name.isEmpty()) return null;
        // Registry lookup — works on Paper 1.21.4 and Spigot/Arclight 1.21+
        try {
            Sound s = Registry.SOUNDS.get(NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT)));
            if (s != null) return s;
        } catch (Exception ignored) {
        }
        // Reflection fallback for older Bukkit where Sound is still an enum.
        // Must NOT call Sound.valueOf() directly — Paper compiles it as InterfaceMethodref.
        try {
            java.lang.reflect.Method m = Sound.class.getMethod("valueOf", String.class);
            return (Sound) m.invoke(null, name.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Returns a HoverEvent for an ItemStack. Paper has ItemStack.asHoverEvent() natively;
     * Bukkit/Arclight does not. Falls back to a text hover showing the item type name.
     */
    @SuppressWarnings("unchecked")
    public static HoverEvent<?> itemHoverEvent(ItemStack item) {
        try {
            java.lang.reflect.Method m = ItemStack.class.getMethod("asHoverEvent");
            return (HoverEvent<?>) m.invoke(item);
        } catch (Exception ignored) {
        }
        // Fallback: show item name as text hover
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return HoverEvent.showText(Component.text(name));
    }

    public static void broadcast(Component component) {
        if (PAPER_SENDER_COMPONENT) {
            Bukkit.broadcast(component);
        } else {
            Bukkit.broadcastMessage(LegacyComponentSerializer.legacySection().serialize(component));
        }
    }
}
