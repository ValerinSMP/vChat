package me.marti.vchat.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

public class MessageSanitizer {

    /**
     * Prepares a message by escaping unauthorized tags and translating allowed
     * legacy codes.
     */
    public static String prepare(Player player, String message) {
        if (player.hasPermission("vchat.format.bypass")) {
            // If bypass, we still want to support both & and MiniMessage tags.
            // We translate & to tags first, then parse.
            return translateLegacyToMiniMessage(message, true, true, true, true);
        }

        boolean allowBasic = player.hasPermission("vchat.format.color.basic");
        boolean allowHex = player.hasPermission("vchat.format.color.hex");
        boolean allowStyles = player.hasPermission("vchat.format.style.basic");
        boolean allowMagic = player.hasPermission("vchat.format.style.magic");

        // 1. First, we escape the message so MiniMessage tags typed by player are
        // literal.
        String escaped = MiniMessage.miniMessage().escapeTags(message);

        // 2. Then, we translate only the legacy codes they are allowed to use.
        return translateLegacyToMiniMessage(escaped, allowBasic, allowHex, allowStyles, allowMagic);
    }

    /**
     * Parses the prepared message into a Component, enforcing permissions and
     * including item support.
     */
    public static Component parse(Player player, String processedMessage, Component itemComponent) {
        if (player.hasPermission("vchat.format.bypass")) {
            java.util.List<TagResolver> bypassResolvers = new java.util.ArrayList<>();
            if (itemComponent != null) {
                bypassResolvers.add(net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("item_tag",
                        itemComponent));
            }
            return MiniMessage.miniMessage().deserialize(processedMessage, TagResolver.resolver(bypassResolvers));
        }

        boolean allowBasic = player.hasPermission("vchat.format.color.basic");
        boolean allowHex = player.hasPermission("vchat.format.color.hex");
        boolean allowStyles = player.hasPermission("vchat.format.style.basic");
        boolean allowMagic = player.hasPermission("vchat.format.style.magic");

        // 3. Build a TagResolver with ONLY allowed tags.
        java.util.List<TagResolver> resolvers = new java.util.ArrayList<>();
        if (allowBasic)
            resolvers.add(StandardTags.color());
        if (allowStyles) {
            // Decorations except magic
            resolvers.add(StandardTags.decorations(TextDecoration.BOLD));
            resolvers.add(StandardTags.decorations(TextDecoration.ITALIC));
            resolvers.add(StandardTags.decorations(TextDecoration.STRIKETHROUGH));
            resolvers.add(StandardTags.decorations(TextDecoration.UNDERLINED));
            resolvers.add(StandardTags.reset());
        }
        if (allowMagic) {
            resolvers.add(StandardTags.decorations(TextDecoration.OBFUSCATED));
        }
        if (allowHex) {
            resolvers.add(StandardTags.color());
            resolvers.add(StandardTags.gradient());
            resolvers.add(StandardTags.rainbow());
        }

        // Add System Tags
        resolvers.add(StandardTags.color()); // For mentions
        resolvers.add(StandardTags.decorations()); // For mentions

        if (itemComponent != null) {
            resolvers.add(
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("item_tag", itemComponent));
        }

        // 4. Parse with ONLY those tags.
        // Note: Standard MiniMessage has all tags. We use a custom one.
        MiniMessage restricted = MiniMessage.builder()
                .tags(TagResolver.resolver(resolvers))
                .build();

        return restricted.deserialize(processedMessage);
    }

    private static String translateLegacyToMiniMessage(String message, boolean basic, boolean hex, boolean styles,
            boolean magic) {
        String result = message;

        if (hex) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(result);
            java.lang.StringBuffer sb = new java.lang.StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }

        if (basic) {
            result = result.replace("&0", "<black>").replace("&1", "<dark_blue>")
                    .replace("&2", "<dark_green>").replace("&3", "<dark_aqua>")
                    .replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                    .replace("&6", "<gold>").replace("&7", "<gray>")
                    .replace("&8", "<dark_gray>").replace("&9", "<blue>")
                    .replace("&a", "<green>").replace("&b", "<aqua>")
                    .replace("&c", "<red>").replace("&d", "<light_purple>")
                    .replace("&e", "<yellow>").replace("&f", "<white>");
        }

        if (styles) {
            result = result.replace("&l", "<bold>").replace("&m", "<strikethrough>")
                    .replace("&n", "<underlined>").replace("&o", "<italic>")
                    .replace("&r", "<reset>");
        }

        if (magic) {
            result = result.replace("&k", "<obfuscated>");
        }

        return result;
    }
}