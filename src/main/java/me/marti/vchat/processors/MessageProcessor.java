package me.marti.vchat.processors;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageProcessor {

    private final MiniMessage miniMessage;
    private final net.luckperms.api.LuckPerms luckPerms;
    private final me.marti.vchat.VChat plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?:/[^\\s]*)?)\\b");

    public MessageProcessor(me.marti.vchat.VChat plugin, net.luckperms.api.LuckPerms luckPerms) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
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

        // 1. Parse prefix/suffix via LegacyComponentSerializer so that &l+hex combos
        //    like &8[&#C62F35&lᴏ...&r] render correctly. String-replacing them into
        //    the MiniMessage format breaks bold/reset scoping.
        LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.builder()
                .character('&').hexColors().build();
        Component prefixComp = legacyAmp.deserialize(prefix);
        Component suffixComp = legacyAmp.deserialize(suffix);

        // 2. Process string placeholders (PAPI mostly) — keep {prefix}/{suffix} as
        //    sentinel tags so we can inject them as Components later.
        String processed = format
                .replace("{prefix}", "<lp_prefix>")
                .replace("{suffix}", "<lp_suffix>");

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            processed = PlaceholderAPI.setPlaceholders(player, processed);
        }

        // 3. Prepare Name Components with Hover
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

        // Replace remaining placeholders with MiniMessage tags
        processed = processed.replace("{name}", "<user_name>")
                .replace("{displayname}", "<user_displayname>")
                .replace("{message}", "<chat_message>");

        // Translate legacy colors in the FORMAT string only (not in prefix/suffix —
        // those are already parsed as Components above).
        processed = translateLegacyHexToMiniMessage(processed);
        processed = translateLegacyToMiniMessage(processed);

        if (plugin.isDebugMode() && containsGlyphToken(processed)) {
            plugin.debugLog("Main format contains glyph token for " + player.getName() + ": " + processed);
        }

        // Deserialize with Resolvers — prefix/suffix injected as pre-parsed Components
        Component formatted = deserializeFormatWithOptionalNexo(player, processed,
                Placeholder.component("lp_prefix", prefixComp),
                Placeholder.component("lp_suffix", suffixComp),
                Placeholder.component("user_name", nameComp),
                Placeholder.component("user_displayname", displayNameComp),
                Placeholder.component("chat_message", message));

        return makeUrlsClickable(formatted);
    }

    public Component getItemComponent(Player viewer, ItemStack item) {
        ItemStack snapshot = item.clone();
        String format = plugin.getConfigManager().getFormats().getString("item-format",
                "<dark_gray>[</dark_gray><aqua>{amount}x {item}</aqua><dark_gray>]</dark_gray>");

        java.util.UUID itemId = plugin.getItemViewManager().cacheItem(snapshot);
        Component fullItem = ItemChatFormatter.render(format, snapshot);

        net.kyori.adventure.text.event.HoverEvent<?> hover = me.marti.vchat.utils.PlatformUtil.itemHoverEvent(snapshot);
        return fullItem.hoverEvent(hover)
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

    private boolean containsGlyphToken(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String lower = input.toLowerCase();
        return lower.contains("<glyph:") || lower.contains("glyph:");
    }

    // §x§R§R§G§G§B§B legacy hex format injected by PAPI placeholders (e.g. DeluxeTags)
    private static final Pattern SECTION_HEX_PATTERN = Pattern.compile(
            "§x(§[0-9A-Fa-f])(§[0-9A-Fa-f])(§[0-9A-Fa-f])(§[0-9A-Fa-f])(§[0-9A-Fa-f])(§[0-9A-Fa-f])");

    private String translateLegacyHexToMiniMessage(String message) {
        // First: convert §x§R§R§G§G§B§B → <#RRGGBB>
        Matcher sectionHex = SECTION_HEX_PATTERN.matcher(message);
        StringBuffer sb1 = new StringBuffer();
        while (sectionHex.find()) {
            StringBuilder hex = new StringBuilder("#");
            for (int i = 1; i <= 6; i++) {
                hex.append(sectionHex.group(i).charAt(1));
            }
            sectionHex.appendReplacement(sb1, Matcher.quoteReplacement("<" + hex + ">"));
        }
        sectionHex.appendTail(sb1);
        message = sb1.toString();

        // Then: convert &#RRGGBB → <#RRGGBB>
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement("<#" + hex + ">"));
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
