package me.marti.vchat.processors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

final class ItemChatFormatter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private ItemChatFormatter() {
    }

    static Component render(String format, ItemStack snapshot) {
        return render(format, snapshot.getAmount(), visibleName(snapshot));
    }

    static Component render(String format, int amount, Component visibleName) {
        String resolvedFormat = format == null || format.isBlank()
                ? "<dark_gray>[</dark_gray><aqua>{amount}x {item}</aqua><dark_gray>]</dark_gray>"
                : format;

        resolvedFormat = resolvedFormat.replace("{amount}", "<amount>")
                .replace("{item}", "<item>");

        return MINI_MESSAGE.deserialize(resolvedFormat,
                Placeholder.parsed("amount", String.valueOf(amount)),
                Placeholder.component("item", visibleName));
    }

    private static final boolean HAS_DISPLAY_NAME_COMPONENT;
    private static final boolean HAS_ITEM_NAME_COMPONENT;

    static {
        boolean displayName = false;
        boolean itemName = false;
        try {
            Method m = ItemMeta.class.getMethod("displayName");
            displayName = m.getReturnType() == Component.class;
        } catch (NoSuchMethodException ignored) {
        }
        try {
            Method m = ItemMeta.class.getMethod("itemName");
            itemName = m.getReturnType() == Component.class;
        } catch (NoSuchMethodException ignored) {
        }
        HAS_DISPLAY_NAME_COMPONENT = displayName;
        HAS_ITEM_NAME_COMPONENT = itemName;
    }

    static Component visibleName(ItemStack snapshot) {
        ItemMeta meta = snapshot.getItemMeta();
        if (meta != null) {
            if (HAS_DISPLAY_NAME_COMPONENT) {
                Component displayName = meta.displayName();
                if (displayName != null) {
                    return displayName;
                }
            } else if (meta.hasDisplayName()) {
                @SuppressWarnings("deprecation")
                String legacy = meta.getDisplayName();
                if (legacy != null && !legacy.isEmpty()) {
                    return LegacyComponentSerializer.legacySection().deserialize(legacy);
                }
            }

            if (HAS_ITEM_NAME_COMPONENT) {
                Component itemName = meta.itemName();
                if (itemName != null) {
                    return itemName;
                }
            }
        }

        return Component.text(prettifyMaterialName(snapshot.getType().name()));
    }

    static String prettifyMaterialName(String name) {
        if (name == null) {
            return "";
        }
        String[] words = name.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }
}
