package me.marti.vchat.processors;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageProcessor {

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final net.luckperms.api.LuckPerms luckPerms;
    private final me.marti.vchat.VChat plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/[^\\s]*)?)\\b");

    public MessageProcessor(me.marti.vchat.VChat plugin, net.luckperms.api.LuckPerms luckPerms) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacyAmpersand();
        this.luckPerms = luckPerms;
    }

    /**
     * Process the chat format and message into a single Component.
     */
    public Component process(Player player, String format, Component message) {
        // 0. Get LuckPerms Data
        net.luckperms.api.cacheddata.CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class)
                .getMetaData(player);
        String prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
        String suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";

        // Pre-parse PAPI in prefix/suffix (Fix for placeholders inside LuckPerms
        // prefixes)
        // Pre-parse PAPI in prefix/suffix (Fix for placeholders inside LuckPerms
        // prefixes)
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            prefix = PlaceholderAPI.setPlaceholders(player, prefix);
            suffix = PlaceholderAPI.setPlaceholders(player, suffix);
        }

        if (plugin.isDebugMode() && (containsGlyphToken(prefix) || containsGlyphToken(suffix))) {
            plugin.debugLog("Glyph token in meta for " + player.getName() + " | prefix='" + prefix + "' | suffix='"
                    + suffix + "'");
        }

        // 1. Process string placeholders (PAPI mostly)
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
            hoverFormat = plugin.getConfigManager().getFormats().getString("name-hover", "");
        }

        Component hoverComponent = null;
        if (hoverFormat != null && !hoverFormat.isEmpty()) {
            hoverFormat = hoverFormat.replace("\\n", "<newline>")
                    .replace("{prefix}", prefix)
                    .replace("{suffix}", suffix);

            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                hoverFormat = PlaceholderAPI.setPlaceholders(player, hoverFormat);
            }
            hoverFormat = translateLegacyHexToMiniMessage(hoverFormat);
            hoverFormat = translateLegacyToMiniMessage(hoverFormat);

            if (plugin.isDebugMode() && containsGlyphToken(hoverFormat)) {
                plugin.debugLog("Hover format contains glyph token for " + player.getName() + ": " + hoverFormat);
            }

            hoverComponent = deserializeHoverWithOptionalNexo(player, hoverFormat);
        }

        Component nameComp = Component.text(player.getName());
        Component displayNameComp = LegacyComponentSerializer.legacySection().deserialize(player.getDisplayName());

        if (hoverComponent != null) {
            nameComp = nameComp.hoverEvent(hoverComponent);
            displayNameComp = displayNameComp.hoverEvent(hoverComponent);
        }

        // Replace placeholders with tags
        processed = processed.replace("{name}", "<user_name>")
                .replace("{displayname}", "<user_displayname>")
                .replace("{message}", "<chat_message>");

        // Translate Colors in the FORMAT ONLY
        processed = translateLegacyHexToMiniMessage(processed);
        processed = translateLegacyToMiniMessage(processed);

        if (plugin.isDebugMode() && containsGlyphToken(processed)) {
            plugin.debugLog("Main format contains glyph token for " + player.getName() + ": " + processed);
        }

        // Deserialize with Resolvers
        Component formatted = deserializeFormatWithOptionalNexo(player, processed,
                Placeholder.component("user_name", nameComp),
                Placeholder.component("user_displayname", displayNameComp),
                Placeholder.component("chat_message", message));

        return makeUrlsClickable(formatted);
    }

    public Component getItemComponent(Player viewer, ItemStack item) {
        String format = plugin.getConfigManager().getFormats().getString("item-format",
                "<dark_gray>[</dark_gray><aqua>{amount}x {item}</aqua><dark_gray>]</dark_gray>");

        // Convert config placeholders {} to MiniMessage tags <>
        format = format.replace("{amount}", "<amount>")
                .replace("{item}", "<item>");

        // Resolve item name
        Component itemName;

        // Fetch Meta ONCE to ensure consistency
        // Fetch Meta ONCE to ensure consistency
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // 1. Custom Name (Anvil/Command - usually italic)
            if (meta.hasDisplayName()) {
                Component displayComp = meta.displayName();

                if (displayComp != null) {
                    itemName = displayComp;
                } else {
                    // Fallback to legacy string if component is null (unexpected but safe)
                    String rawName = meta.getDisplayName();
                    // Check for legacy codes
                    if (rawName.contains(LegacyComponentSerializer.SECTION_CHAR + "")) {
                        itemName = LegacyComponentSerializer.legacySection().deserialize(rawName);
                    } else {
                        rawName = translateLegacyHexToMiniMessage(rawName);
                        rawName = translateLegacyToMiniMessage(rawName);
                        itemName = miniMessage.deserialize(rawName);
                    }
                }
            }
            // 1.5 Nexo Custom Item Name
            else {
                Component nexoName = resolveNexoItemName(item);
                if (nexoName != null) {
                    itemName = nexoName;
                }
                // 2. Item Name (Data Component - usually normal text, used by Nexo/Oraxen
                // 1.21+)
                else if (meta.hasItemName()) {
                    itemName = sanitizePotentialGlyphName(meta.itemName(), item.getType());
                }
                // 3. Fallback to Material Name
                else {
                    itemName = Component.text(prettifyEnumName(item.getType().name()));
                }
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

        ItemStack hoverItem = createHoverItemSnapshot(viewer, item);

        return fullItem.hoverEvent(hoverItem.asHoverEvent())
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/vchat viewitem " + itemId));
    }

    public Component parseMiniMessageForPlayer(Player player, String input, TagResolver... resolvers) {
        if (input == null) {
            return Component.empty();
        }

        String processed = translateLegacyHexToMiniMessage(input);
        processed = translateLegacyToMiniMessage(processed);
        return deserializeFormatWithOptionalNexo(player, processed, resolvers);
    }

    private ItemStack createHoverItemSnapshot(Player viewer, ItemStack item) {
        ItemStack snapshot = item.clone();
        ItemMeta snapshotMeta = snapshot.getItemMeta();
        if (snapshotMeta == null) {
            return snapshot;
        }

        boolean apiReportsLore = snapshotMeta.hasLore();
        int adventureLoreCount = snapshotMeta.lore() == null ? 0 : snapshotMeta.lore().size();
        int legacyLoreCount = getLegacyLoreCount(snapshotMeta);
        List<Component> originalLore = extractLoreComponents(snapshotMeta);
        if (originalLore.isEmpty()) {
            List<Component> nexoLore = resolveNexoItemLore(snapshot);
            if (!nexoLore.isEmpty()) {
                originalLore.addAll(nexoLore);
                if (plugin.isDebugMode()) {
                    plugin.debugLog("ItemHoverDebug using Nexo lore fallback lines=" + nexoLore.size());
                }
            }
        }

        List<Component> enchantLines = buildReadableEnchantLore(viewer, snapshot, snapshotMeta);
        List<Component> mergedLore = new ArrayList<>();
        if (!enchantLines.isEmpty()) {
            mergedLore.addAll(enchantLines);
            if (!originalLore.isEmpty()) {
                mergedLore.add(Component.empty());
            }
        }

        if (!originalLore.isEmpty()) {
            mergedLore.addAll(originalLore);
        }

        boolean canReplaceLoreSafely = !apiReportsLore || !originalLore.isEmpty();
        if (plugin.isDebugMode()) {
            String viewerName = viewer != null ? viewer.getName() : "unknown";
            plugin.debugLog("ItemHoverDebug viewer='" + viewerName + "' item='" + snapshot.getType() + "' hasLore="
                    + apiReportsLore + " adventureLore=" + adventureLoreCount + " legacyLore=" + legacyLoreCount
                    + " extractedLore=" + originalLore.size() + " enchants=" + enchantLines.size()
                    + " mergedLore=" + mergedLore.size() + " canReplace=" + canReplaceLoreSafely);
            if (!originalLore.isEmpty()) {
                plugin.debugLog("ItemHoverDebug firstLore='" + componentPreview(originalLore.get(0)) + "'");
            }
            if (!enchantLines.isEmpty()) {
                plugin.debugLog("ItemHoverDebug firstEnchant='" + componentPreview(enchantLines.get(0)) + "'");
            }
        }

        if (!mergedLore.isEmpty() && canReplaceLoreSafely) {
            snapshotMeta.lore(mergedLore);
        } else if (!mergedLore.isEmpty() && plugin.isDebugMode()) {
            plugin.debugLog("Hover lore merge skipped to avoid overriding opaque lore data.");
        }

        // Keep original lore and add readable enchant lines while hiding the raw section.
        snapshotMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        snapshot.setItemMeta(snapshotMeta);
        return snapshot;
    }

    private List<Component> buildReadableEnchantLore(Player viewer, ItemStack item, ItemMeta meta) {
        List<Component> lines = new ArrayList<>();

        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            lines.add(normalizeEnchantLine(viewer, entry.getKey().displayName(entry.getValue())));
        }

        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            for (Map.Entry<Enchantment, Integer> entry : storageMeta.getStoredEnchants().entrySet()) {
                lines.add(normalizeEnchantLine(viewer, entry.getKey().displayName(entry.getValue())));
            }
        }

        return lines;
    }

    @SuppressWarnings("deprecation")
    private int getLegacyLoreCount(ItemMeta meta) {
        List<String> legacyLore = meta.getLore();
        return legacyLore == null ? 0 : legacyLore.size();
    }

    @SuppressWarnings("deprecation")
    private List<Component> extractLoreComponents(ItemMeta meta) {
        List<Component> lore = new ArrayList<>();

        List<Component> adventureLore = meta.lore();
        if (adventureLore != null && !adventureLore.isEmpty()) {
            lore.addAll(adventureLore);
            return lore;
        }

        List<String> legacyLore = meta.getLore();
        if (legacyLore != null && !legacyLore.isEmpty()) {
            for (String line : legacyLore) {
                if (line != null && !line.isEmpty()) {
                    lore.add(LegacyComponentSerializer.legacySection().deserialize(line));
                }
            }
        }

        return lore;
    }

    private Component normalizeEnchantLine(Player viewer, Component input) {
        Component parsed = applyNexoGlyphSupport(viewer, input);
        return parsed.decoration(TextDecoration.ITALIC, false);
    }

    private Component applyNexoGlyphSupport(Player viewer, Component input) {
        if (viewer == null || input == null) {
            return input;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(input);
        if (!containsGlyphToken(plain)) {
            return input;
        }

        me.marti.vchat.compat.NexoHook hook = plugin.getNexoHook();
        if (hook == null) {
            return input;
        }

        Component parsed = hook.deserializeForPlayer(viewer, plain);
        return parsed != null ? parsed : input;
    }

    private String componentPreview(Component component) {
        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        if (plain.length() <= 80) {
            return plain;
        }
        return plain.substring(0, 80) + "...";
    }

    private Component resolveNexoItemName(ItemStack item) {
        me.marti.vchat.compat.NexoHook hook = plugin.getNexoHook();
        if (hook == null) {
            return null;
        }
        return hook.resolveItemDisplayName(item);
    }

    private List<Component> resolveNexoItemLore(ItemStack item) {
        me.marti.vchat.compat.NexoHook hook = plugin.getNexoHook();
        if (hook == null) {
            return List.of();
        }
        return hook.resolveItemLore(item);
    }

    private Component deserializeHoverWithOptionalNexo(Player player, String hoverFormat) {
        me.marti.vchat.compat.NexoHook hook = plugin.getNexoHook();
        if (hook != null) {
            Component nexoRendered = hook.deserializeForPlayer(player, hoverFormat);
            if (nexoRendered != null) {
                if (plugin.isDebugMode() && containsGlyphToken(hoverFormat)) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(nexoRendered);
                    plugin.debugLog("Hover parsed with Nexo parser for " + player.getName() + " | plain='" + plain
                            + "'");
                }
                return nexoRendered;
            }
        }

        if (plugin.isDebugMode() && containsGlyphToken(hoverFormat)) {
            plugin.debugLog("Hover fallback to default MiniMessage parser for " + player.getName());
        }
        return miniMessage.deserialize(hoverFormat);
    }

    private Component deserializeFormatWithOptionalNexo(Player player, String processed,
            TagResolver... placeholders) {
        me.marti.vchat.compat.NexoHook hook = plugin.getNexoHook();
        if (hook != null) {
            Component nexoRendered = hook.deserializeForPlayer(player, processed, placeholders);
            if (nexoRendered != null) {
                if (plugin.isDebugMode() && containsGlyphToken(processed)) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(nexoRendered);
                    plugin.debugLog("Main format parsed with Nexo parser for " + player.getName() + " | plain='"
                            + plain + "'");
                }
                return nexoRendered;
            }
        }

        if (plugin.isDebugMode() && containsGlyphToken(processed)) {
            plugin.debugLog("Main format fallback to default MiniMessage parser for " + player.getName());
        }
        return miniMessage.deserialize(processed, placeholders);
    }

    private Component sanitizePotentialGlyphName(Component rawName, Material material) {
        if (rawName == null) {
            return Component.text(prettifyEnumName(material.name()));
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(rawName);
        if (plain.toLowerCase().startsWith("glyph:")) {
            return Component.text(prettifyEnumName(material.name()));
        }
        return rawName;
    }

    private boolean containsGlyphToken(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String lower = input.toLowerCase();
        return lower.contains("<glyph:") || lower.contains("glyph:");
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

    private Component makeUrlsClickable(Component component) {
        return component.replaceText(TextReplacementConfig.builder()
                .match(URL_PATTERN)
                .replacement((match, builder) -> {
                    String raw = match.group(1);
                    String target = normalizeUrl(raw);
                    if (target == null) {
                        return builder.build();
                    }
                    return builder
                            .clickEvent(ClickEvent.openUrl(target))
                            .build();
                })
                .build());
    }

    private String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String lower = raw.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return raw;
        }

        if (lower.startsWith("www.") || raw.contains(".")) {
            return "https://" + raw;
        }

        return null;
    }
}
