package me.marti.vchat.processors;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageProcessor {

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final net.luckperms.api.LuckPerms luckPerms;
    private final me.marti.vchat.VChat plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

    public MessageProcessor(me.marti.vchat.VChat plugin, net.luckperms.api.LuckPerms luckPerms) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.luckPerms = luckPerms;
    }

    /**
     * Process the chat format and message into a single Component.
     */
    public Component process(Player player, String format, String message) {
        // 0. Get LuckPerms Data
        net.luckperms.api.cacheddata.CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class)
                .getMetaData(player);
        String prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
        String suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";

        // 1. Process string placeholders (PAPI mostly)
        // Note: We do NOT replace {name} or {displayname} here yet if we want hover on
        // them.
        // But we DO replace {prefix}/{suffix} and PAPI.

        String processed = format
                .replace("{prefix}", prefix)
                .replace("{suffix}", suffix);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            processed = PlaceholderAPI.setPlaceholders(player, processed);
        }

        // 2. Prepare Name Components with Hover
        java.util.List<String> hoverLines = plugin.getConfigManager().getFormats().getStringList("name-hover");
        String hoverFormat;

        if (!hoverLines.isEmpty()) {
            hoverFormat = String.join("<newline>", hoverLines);
        } else {
            // Fallback for single string or if list is empty but key exists as string
            hoverFormat = plugin.getConfigManager().getFormats().getString("name-hover", "");
        }

        Component hoverComponent = null;
        if (hoverFormat != null && !hoverFormat.isEmpty()) {
            // Support for \n as newline for convenience (legacy support)
            hoverFormat = hoverFormat.replace("\\n", "<newline>");

            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                hoverFormat = PlaceholderAPI.setPlaceholders(player, hoverFormat);
            }
            hoverFormat = translateLegacyHexToMiniMessage(hoverFormat);
            hoverFormat = translateLegacyToMiniMessage(hoverFormat); // Ensure colors are MiniMessage friendly
            hoverComponent = miniMessage.deserialize(hoverFormat);
        }

        Component nameComp = Component.text(player.getName());
        Component displayNameComp = legacySerializer.deserialize(player.getDisplayName());

        if (hoverComponent != null) {
            nameComp = nameComp.hoverEvent(hoverComponent);
            displayNameComp = displayNameComp.hoverEvent(hoverComponent);
        }

        // Replace {name} and {displayname} with tags to resolve later
        processed = processed.replace("{name}", "<user_name>")
                .replace("{displayname}", "<user_displayname>");

        // 3. Handle Colors & Item Placeholder in Message
        // Check for [item] or [i]
        boolean hasItemPlaceholder = message.toLowerCase().contains("[item]") || message.toLowerCase().contains("[i]");
        ItemStack handItem = player.getInventory().getItemInMainHand();

        // ... (Item logic is similar, just resolve <item_tag>)
        Component itemComponent = Component.empty();
        if (hasItemPlaceholder && handItem.getType() != Material.AIR) {
            itemComponent = getItemComponent(handItem);
            message = message.replaceAll("(?i)\\[item]|(?i)\\[i]", "<item_tag>");
        }

        // Insert message
        processed = processed.replace("{message}", message);

        // Translate Colors
        processed = translateLegacyHexToMiniMessage(processed);
        processed = translateLegacyToMiniMessage(processed);

        // Deserialize with Resolvers
        return miniMessage.deserialize(processed,
                Placeholder.component("user_name", nameComp),
                Placeholder.component("user_displayname", displayNameComp),
                Placeholder.component("item_tag", itemComponent) // Always pass it, empty if unused is fine? No, empty
                                                                 // component is fine.
        );
    }

    public Component getItemComponent(ItemStack item) {
        String format = plugin.getConfigManager().getFormats().getString("item-format",
                "<dark_gray>[</dark_gray><aqua>{amount}x {item}</aqua><dark_gray>]</dark_gray>");

        // Convert config placeholders {} to MiniMessage tags <>
        format = format.replace("{amount}", "<amount>")
                .replace("{item}", "<item>");

        // Resolve item name
        Component itemName;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String rawName = item.getItemMeta().getDisplayName();

            // Check for legacy codes (Section symbol §) to prevent MiniMessage parsing
            // errors
            if (rawName.contains(LegacyComponentSerializer.SECTION_CHAR + "")) {
                // It's a legacy string (likely from another plugin or NBT)
                itemName = LegacyComponentSerializer.legacySection().deserialize(rawName);
            } else {
                // Process & codes manually and then parse as MiniMessage
                rawName = translateLegacyHexToMiniMessage(rawName);
                rawName = translateLegacyToMiniMessage(rawName);
                itemName = miniMessage.deserialize(rawName);
            }
        } else {
            itemName = Component.text(prettifyEnumName(item.getType().name()));
        }

        // Cache Item for View
        java.util.UUID itemId = plugin.getItemViewManager().cacheItem(item);

        // Build the component with hover and click
        Component fullItem = miniMessage.deserialize(format,
                Placeholder.parsed("amount", String.valueOf(item.getAmount())),
                Placeholder.component("item", itemName));

        return fullItem.hoverEvent(item.asHoverEvent())
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/vchat viewitem " + itemId));
    }

    private String translateLegacyHexToMiniMessage(String message) {
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, "<#" + hex + ">");
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String translateLegacyToMiniMessage(String message) {
        // Simple mapping for standard colors
        return message
                .replace("&0", "<black>")
                .replace("&1", "<dark_blue>")
                .replace("&2", "<dark_green>")
                .replace("&3", "<dark_aqua>")
                .replace("&4", "<dark_red>")
                .replace("&5", "<dark_purple>")
                .replace("&6", "<gold>")
                .replace("&7", "<gray>")
                .replace("&8", "<dark_gray>")
                .replace("&9", "<blue>")
                .replace("&a", "<green>")
                .replace("&b", "<aqua>")
                .replace("&c", "<red>")
                .replace("&d", "<light_purple>")
                .replace("&e", "<yellow>")
                .replace("&f", "<white>")
                .replace("&l", "<bold>")
                .replace("&m", "<strikethrough>")
                .replace("&n", "<underlined>")
                .replace("&o", "<italic>")
                .replace("&r", "<reset>")
                // Handle Section Symbols (Server Internals)
                .replace("\u00A70", "<black>")
                .replace("\u00A71", "<dark_blue>")
                .replace("\u00A72", "<dark_green>")
                .replace("\u00A73", "<dark_aqua>")
                .replace("\u00A74", "<dark_red>")
                .replace("\u00A75", "<dark_purple>")
                .replace("\u00A76", "<gold>")
                .replace("\u00A77", "<gray>")
                .replace("\u00A78", "<dark_gray>")
                .replace("\u00A79", "<blue>")
                .replace("\u00A7a", "<green>")
                .replace("\u00A7b", "<aqua>")
                .replace("\u00A7c", "<red>")
                .replace("\u00A7d", "<light_purple>")
                .replace("\u00A7e", "<yellow>")
                .replace("\u00A7f", "<white>")
                .replace("\u00A7l", "<bold>")
                .replace("\u00A7m", "<strikethrough>")
                .replace("\u00A7n", "<underlined>")
                .replace("\u00A7o", "<italic>")
                .replace("\u00A7r", "<reset>");
    }

    private String prettifyEnumName(String name) {
        if (name == null)
            return "";
        String[] words = name.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty())
                continue;
            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return builder.toString().trim();
    }
}
