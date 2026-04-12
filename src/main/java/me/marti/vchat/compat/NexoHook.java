package me.marti.vchat.compat;

import com.nexomc.nexo.api.NexoItems;
import me.marti.vchat.VChat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NexoHook {

    private final VChat plugin;

    public NexoHook(VChat plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        // Reserved for future listeners/callback cleanup.
    }

    public Component resolveItemDisplayName(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }

        try {
            String itemId = NexoItems.idFromItem(item);
            if (itemId == null || itemId.isBlank()) {
                plugin.debugLog("NexoHook: item has no Nexo id, skipping custom name resolution.");
                return null;
            }

            var itemBuilder = NexoItems.itemFromId(itemId);
            if (itemBuilder == null) {
                return null;
            }

            ItemStack built = itemBuilder.build();
            if (built == null) {
                return null;
            }

            ItemMeta meta = built.getItemMeta();
            if (meta == null) {
                return null;
            }

            if (meta.hasDisplayName() && meta.displayName() != null) {
                plugin.debugLog("NexoHook: resolved displayName for itemId='" + itemId + "'.");
                return meta.displayName();
            }

            if (meta.hasItemName() && meta.itemName() != null) {
                String plain = PlainTextComponentSerializer.plainText().serialize(meta.itemName());
                if (!plain.toLowerCase().startsWith("glyph:")) {
                    plugin.debugLog("NexoHook: resolved itemName for itemId='" + itemId + "'.");
                    return meta.itemName();
                }
            }
        } catch (Throwable ignored) {
            plugin.debugLog("NexoHook: exception while resolving item display name: " + ignored.getClass().getSimpleName()
                    + " - " + ignored.getMessage());
            // Nexo is optional; failures here should never break chat flow.
        }

        return null;
    }

    public List<Component> resolveItemLore(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return List.of();
        }

        try {
            List<Component> fromExistingBuilder = extractLoreFromBuilder(NexoItems.builderFromItem(item),
                    "builderFromItem");
            if (!fromExistingBuilder.isEmpty()) {
                return fromExistingBuilder;
            }

            String itemId = NexoItems.idFromItem(item);
            if (itemId == null || itemId.isBlank()) {
                return List.of();
            }

            var itemBuilder = NexoItems.itemFromId(itemId);
            if (itemBuilder == null) {
                plugin.debugLog("NexoHook: no itemBuilder found for itemId='" + itemId + "'.");
                return List.of();
            }

            List<Component> fromTemplateBuilder = extractLoreFromBuilder(itemBuilder, "itemFromId");
            if (!fromTemplateBuilder.isEmpty()) {
                plugin.debugLog("NexoHook: resolved lore from itemId='" + itemId + "' lines="
                        + fromTemplateBuilder.size() + ".");
                return fromTemplateBuilder;
            }

            ItemStack built = itemBuilder.build();
            if (built == null) {
                plugin.debugLog("NexoHook: builder returned null item for itemId='" + itemId + "'.");
                return List.of();
            }

            ItemMeta meta = built.getItemMeta();
            if (meta == null) {
                plugin.debugLog("NexoHook: built item has null meta for itemId='" + itemId + "'.");
                return List.of();
            }

            List<Component> lore = extractLoreFromMeta(meta);
            if (meta.lore() != null && !meta.lore().isEmpty()) {
                // No-op: already extracted through helper.
            }

            if (lore.isEmpty()) {
                lore.addAll(extractLoreFromBuilderReflectively(itemBuilder));
            }

            if (!lore.isEmpty()) {
                plugin.debugLog("NexoHook: resolved lore from itemId='" + itemId + "' lines=" + lore.size() + ".");
            } else {
                plugin.debugLog("NexoHook: lore still empty for itemId='" + itemId + "' after meta+reflection fallback.");
            }
            return lore;
        } catch (Throwable ignored) {
            plugin.debugLog("NexoHook: exception while resolving item lore: " + ignored.getClass().getSimpleName()
                    + " - " + ignored.getMessage());
            return List.of();
        }
    }

    private List<Component> extractLoreFromBuilder(Object itemBuilder, String source) {
        if (itemBuilder == null) {
            return List.of();
        }

        List<Component> lore = new ArrayList<>();
        try {
            if (itemBuilder instanceof com.nexomc.nexo.items.ItemBuilder typedBuilder) {
                lore.addAll(safeComponents(typedBuilder.getLore()));
                lore.addAll(safeComponents(typedBuilder.lore()));
                if (!lore.isEmpty()) {
                    plugin.debugLog("NexoHook: lore extracted via " + source + " ItemBuilder API lines=" + lore.size()
                            + ".");
                    return lore;
                }
            }
        } catch (Throwable ignored) {
            plugin.debugLog("NexoHook: " + source + " typed lore extraction failed: "
                    + ignored.getClass().getSimpleName() + " - " + ignored.getMessage());
        }

        lore.addAll(extractLoreFromBuilderReflectively(itemBuilder));
        if (!lore.isEmpty()) {
            plugin.debugLog("NexoHook: lore extracted via " + source + " reflective fallback lines=" + lore.size() + ".");
        }
        return lore;
    }

    private List<Component> safeComponents(List<Component> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<Component> result = new ArrayList<>();
        for (Component component : input) {
            if (component != null) {
                result.add(component);
            }
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private List<Component> extractLoreFromMeta(ItemMeta meta) {
        List<Component> lore = new ArrayList<>();
        if (meta == null) {
            return lore;
        }

        if (meta.lore() != null && !meta.lore().isEmpty()) {
            lore.addAll(meta.lore());
            return lore;
        }

        List<String> legacyLore = meta.getLore();
        if (legacyLore != null && !legacyLore.isEmpty()) {
            for (String line : legacyLore) {
                if (line != null && !line.isEmpty()) {
                    lore.add(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                            .legacySection().deserialize(line));
                }
            }
        }
        return lore;
    }

    @SuppressWarnings("unchecked")
    private List<Component> extractLoreFromBuilderReflectively(Object itemBuilder) {
        List<Component> lore = new ArrayList<>();
        if (itemBuilder == null) {
            return lore;
        }

        String[] candidates = new String[] { "getLore", "lore", "parsedLore", "getParsedLore", "displayLore" };

        for (String methodName : candidates) {
            try {
                Method method = itemBuilder.getClass().getMethod(methodName);
                Object result = method.invoke(itemBuilder);
                if (result instanceof Collection<?> collection && !collection.isEmpty()) {
                    for (Object line : collection) {
                        if (line instanceof Component component) {
                            lore.add(component);
                        } else if (line instanceof String stringLine && !stringLine.isEmpty()) {
                            lore.add(MiniMessage.miniMessage().deserialize(stringLine));
                        }
                    }
                    if (!lore.isEmpty()) {
                        plugin.debugLog("NexoHook: lore extracted via builder method '" + methodName + "'.");
                        return lore;
                    }
                }
            } catch (Throwable ignored) {
                // Try next candidate.
            }
        }

        return lore;
    }

    public Component deserializeForPlayer(Player player, String input) {
        return deserializeForPlayer(player, input, new TagResolver[0]);
    }

    public Component deserializeForPlayer(Player player, String input, TagResolver... resolvers) {
        if (player == null || input == null || input.isBlank()) {
            return null;
        }

        TagResolver[] safeResolvers = resolvers == null ? new TagResolver[0] : resolvers;

        Component parsed = tryMiniMessagePlayer(player, input, safeResolvers);
        if (isAcceptableParsedResult(input, parsed)) {
            return parsed;
        }

        parsed = tryAdventureUtilsMiniMessage(player, input, safeResolvers);
        if (isAcceptableParsedResult(input, parsed)) {
            return parsed;
        }

        parsed = tryGlyphTagMiniMessage(player, input, safeResolvers);
        if (isAcceptableParsedResult(input, parsed)) {
            return parsed;
        }

        plugin.debugLog("NexoHook: all parser strategies failed for player='" + player.getName() + "'.");
        return null;
    }

    private boolean isAcceptableParsedResult(String originalInput, Component parsed) {
        if (parsed == null) {
            return false;
        }

        if (!containsGlyphToken(originalInput)) {
            return true;
        }

        String plain = PlainTextComponentSerializer.plainText().serialize(parsed);
        return !containsGlyphToken(plain);
    }

    private boolean containsGlyphToken(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.toLowerCase().contains("<glyph:");
    }

    private Component tryMiniMessagePlayer(Player player, String input, TagResolver... resolvers) {
        try {
            Class<?> adventureUtilsClass = Class.forName("com.nexomc.nexo.utils.AdventureUtils");
            Method method = adventureUtilsClass.getMethod("MINI_MESSAGE_PLAYER", Player.class);

            Object target = null;
            if (!Modifier.isStatic(method.getModifiers())) {
                target = resolveAdventureUtilsInstance(adventureUtilsClass);
                if (target == null) {
                    throw new IllegalStateException("AdventureUtils instance unavailable for MINI_MESSAGE_PLAYER");
                }
            }

            Object result = method.invoke(target, player);
            if (result instanceof MiniMessage miniMessage) {
                plugin.debugLog("NexoHook: strategy #1 MINI_MESSAGE_PLAYER success for player='" + player.getName() + "'.");
                return miniMessage.deserialize(input, resolvers);
            }
        } catch (Throwable t) {
            plugin.debugLog("NexoHook: strategy #1 MINI_MESSAGE_PLAYER failed for player='" + player.getName() + "': "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
        }

        return null;
    }

    private Component tryAdventureUtilsMiniMessage(Player player, String input, TagResolver... resolvers) {
        try {
            Class<?> adventureUtilsClass = Class.forName("com.nexomc.nexo.utils.AdventureUtils");
            Object instance = resolveAdventureUtilsInstance(adventureUtilsClass);
            if (instance == null) {
                return null;
            }

            Method getter = adventureUtilsClass.getMethod("getMINI_MESSAGE");
            Object result = getter.invoke(instance);
            if (result instanceof MiniMessage miniMessage) {
                plugin.debugLog("NexoHook: strategy #2 getMINI_MESSAGE success for player='" + player.getName() + "'.");
                return miniMessage.deserialize(input, resolvers);
            }
        } catch (Throwable t) {
            plugin.debugLog("NexoHook: strategy #2 getMINI_MESSAGE failed for player='" + player.getName() + "': "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
        }

        return null;
    }

    private Component tryGlyphTagMiniMessage(Player player, String input, TagResolver... resolvers) {
        try {
            Class<?> glyphTagClass = Class.forName("com.nexomc.nexo.glyphs.GlyphTag");
            Field instanceField = glyphTagClass.getField("INSTANCE");
            Object glyphTagInstance = instanceField.get(null);
            if (glyphTagInstance == null) {
                return null;
            }

            Method getResolverForPlayer = glyphTagClass.getMethod("getResolverForPlayer", Player.class);
            Object glyphResolverRaw = getResolverForPlayer.invoke(glyphTagInstance, player);
            if (!(glyphResolverRaw instanceof TagResolver glyphResolver)) {
                return null;
            }

            List<TagResolver> allResolvers = new ArrayList<>();
            allResolvers.add(StandardTags.color());
            allResolvers.add(StandardTags.decorations());
            allResolvers.add(StandardTags.reset());
            allResolvers.add(StandardTags.gradient());
            allResolvers.add(StandardTags.rainbow());
            allResolvers.add(glyphResolver);

            for (TagResolver resolver : resolvers) {
                if (resolver != null) {
                    allResolvers.add(resolver);
                }
            }

            MiniMessage localMini = MiniMessage.builder()
                    .tags(TagResolver.resolver(allResolvers))
                    .build();

            plugin.debugLog("NexoHook: strategy #3 GlyphTag resolver success for player='" + player.getName() + "'.");
            return localMini.deserialize(input);
        } catch (Throwable t) {
            plugin.debugLog("NexoHook: strategy #3 GlyphTag resolver failed for player='" + player.getName() + "': "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
        }

        return null;
    }

    private Object resolveAdventureUtilsInstance(Class<?> adventureUtilsClass) {
        try {
            Field instanceField = adventureUtilsClass.getField("INSTANCE");
            Object instance = instanceField.get(null);
            if (instance != null) {
                return instance;
            }
        } catch (Throwable ignored) {
            // Ignore and fallback to ctor.
        }

        try {
            var constructor = adventureUtilsClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
