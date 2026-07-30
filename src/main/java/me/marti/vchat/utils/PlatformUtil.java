package me.marti.vchat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PlatformUtil {

    public static final MiniMessage MM = MiniMessage.miniMessage();

    private PlatformUtil() {
    }

    public static boolean isPaper() {
        return true;
    }

    public static void sendActionBar(Player player, Component component) {
        player.sendActionBar(component);
    }

    public static void sendMessage(CommandSender sender, Component component) {
        sender.sendMessage(component);
    }

    public static Sound resolveSound(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return Registry.SOUNDS.get(
                NamespacedKey.minecraft(name.toLowerCase(java.util.Locale.ROOT)));
    }

    public static HoverEvent<?> itemHoverEvent(ItemStack item) {
        return item.asHoverEvent();
    }

    public static void sendMini(CommandSender sender, String raw, TagResolver... resolvers) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        sendMessage(sender, MM.deserialize(raw, resolvers));
    }

    public static void broadcast(Component component) {
        Bukkit.broadcast(component);
    }
}
