package me.marti.vchat.managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public class ItemViewManager {

    private final Map<UUID, CachedItem> itemCache = new ConcurrentHashMap<>();
    private final me.marti.vchat.VChat plugin;
    private final LongSupplier ttlSecondsSupplier;

    public ItemViewManager(me.marti.vchat.VChat plugin) {
        this(plugin, () -> Math.max(60L,
                plugin.getConfigManager().getMainConfig().getLong("item-view.cache-ttl-seconds", 300L)));
    }

    ItemViewManager(me.marti.vchat.VChat plugin, LongSupplier ttlSecondsSupplier) {
        this.plugin = plugin;
        this.ttlSecondsSupplier = ttlSecondsSupplier;
    }

    public UUID cacheItem(ItemStack item) {
        UUID id = UUID.randomUUID();
        itemCache.put(id, new CachedItem(item.clone(), System.currentTimeMillis())); // Clone for safety
        return id;
    }

    public ItemStack getItem(UUID id) {
        CachedItem cached = itemCache.get(id);
        if (cached == null) {
            return null;
        }
        if (isExpired(cached.createdAt())) {
            itemCache.remove(id);
            return null;
        }
        return cached.item().clone();
    }

    public void openView(Player player, UUID itemId) {
        ItemStack item = getItem(itemId);
        if (item == null) {
            player.sendMessage(
                    Component.text("Item expired or not found.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        String title = plugin.getConfigManager().getFormats().getString("item-view-title", "Item View");
        Component titleComp = MiniMessage.miniMessage().deserialize(title);
        Inventory inv;
        try {
            // Paper API: createInventory with Component title
            inv = (Inventory) Bukkit.class.getMethod("createInventory",
                    org.bukkit.inventory.InventoryHolder.class,
                    org.bukkit.event.inventory.InventoryType.class,
                    Component.class)
                    .invoke(null, new ItemViewHolder(), org.bukkit.event.inventory.InventoryType.DISPENSER, titleComp);
        } catch (Exception ignored) {
            // Bukkit/Arclight fallback: legacy string title
            String legacyTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                    .legacySection().serialize(titleComp);
            inv = Bukkit.createInventory(new ItemViewHolder(), org.bukkit.event.inventory.InventoryType.DISPENSER, legacyTitle);
        }

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        try {
            // Paper API
            ItemMeta.class.getMethod("displayName", Component.class);
            meta.displayName(Component.empty());
        } catch (NoSuchMethodException ignored) {
            setDisplayNameLegacy(meta);
        }
        filler.setItemMeta(meta);

        for (int i = 0; i < 9; i++) {
            if (i == 4) {
                inv.setItem(i, item);
            } else {
                inv.setItem(i, filler);
            }
        }

        player.openInventory(inv);
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        itemCache.entrySet().removeIf(entry -> isExpired(entry.getValue().createdAt(), now));
    }

    public void clear() {
        itemCache.clear();
    }

    private boolean isExpired(long createdAt) {
        return isExpired(createdAt, System.currentTimeMillis());
    }

    private boolean isExpired(long createdAt, long now) {
        long ttlSeconds = Math.max(60L, ttlSecondsSupplier.getAsLong());
        return (now - createdAt) > (ttlSeconds * 1000L);
    }

    @SuppressWarnings("deprecation")
    private static void setDisplayNameLegacy(ItemMeta meta) {
        meta.setDisplayName(" ");
    }

    public static class ItemViewHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return null; // Not needed really, just a marker
        }
    }

    private record CachedItem(ItemStack item, long createdAt) {
    }
}
