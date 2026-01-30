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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ItemViewManager {

    private final Map<UUID, ItemStack> itemCache = new HashMap<>();
    private final me.marti.vchat.VChat plugin;

    public ItemViewManager(me.marti.vchat.VChat plugin) {
        this.plugin = plugin;
    }

    public UUID cacheItem(ItemStack item) {
        UUID id = UUID.randomUUID();
        itemCache.put(id, item.clone()); // Clone for safety
        return id;
    }

    public ItemStack getItem(UUID id) {
        return itemCache.get(id);
    }

    public void openView(Player player, UUID itemId) {
        ItemStack item = itemCache.get(itemId);
        if (item == null) {
            player.sendMessage(
                    Component.text("Item expired or not found.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }

        String title = plugin.getConfig().getString("item-view-title", "Item View");
        Inventory inv = Bukkit.createInventory(new ItemViewHolder(), org.bukkit.event.inventory.InventoryType.DISPENSER,
                MiniMessage.miniMessage().deserialize(title));

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
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

    public static class ItemViewHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return null; // Not needed really, just a marker
        }
    }
}
