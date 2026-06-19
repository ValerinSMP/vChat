package me.marti.vchat.listeners;

import me.marti.vchat.managers.ItemViewManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

public class InventoryListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        // Use getView().getTopInventory() to catch shift-clicks from bottom inventory
        // where getInventory() returns the top but getClickedInventory() returns bottom.
        if (event.getView().getTopInventory().getHolder() instanceof ItemViewManager.ItemViewHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ItemViewManager.ItemViewHolder) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof ItemViewManager.ItemViewHolder
                || event.getDestination().getHolder() instanceof ItemViewManager.ItemViewHolder) {
            event.setCancelled(true);
        }
    }
}
