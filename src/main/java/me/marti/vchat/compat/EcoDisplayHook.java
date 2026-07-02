package me.marti.vchat.compat;

import me.marti.vchat.VChat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * Bridges to Auxilor's "eco" framework (shared by EcoItems, EcoEnchants, EcoArmor).
 * Those plugins render dynamic lore (RPG stats, ability descriptions, tier text) only into
 * outgoing inventory packets via their own netty pipeline injection — it's never persisted
 * on the item's real NBT/ItemMeta, so reading getItemMeta().lore() on a held item returns
 * null/incomplete. eco exposes a public entry point, com.willfp.eco.core.display.Display,
 * that runs the same rendering pipeline synchronously and returns a fully rendered item.
 * Used via reflection since eco isn't a listed compile dependency.
 */
public class EcoDisplayHook {

    private final VChat plugin;
    private final Method displayMethod;

    public EcoDisplayHook(VChat plugin) {
        this.plugin = plugin;
        this.displayMethod = resolveDisplayMethod();
    }

    private Method resolveDisplayMethod() {
        try {
            Class<?> displayClass = Class.forName("com.willfp.eco.core.display.Display");
            return displayClass.getMethod("display", ItemStack.class, Player.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean isAvailable() {
        return displayMethod != null;
    }

    /**
     * Renders eco's dynamic lore into a copy of the given item. Returns the original item
     * unchanged if eco isn't installed or rendering fails for any reason.
     */
    public ItemStack renderDisplay(ItemStack item, Player viewer) {
        if (displayMethod == null || item == null || viewer == null) {
            return item;
        }
        try {
            Object result = displayMethod.invoke(null, item, viewer);
            if (result instanceof ItemStack rendered) {
                plugin.debugLog("EcoDisplayHook: rendered dynamic display for " + item.getType()
                        + " (viewer=" + viewer.getName() + ").");
                return rendered;
            }
        } catch (Throwable t) {
            plugin.debugLog("EcoDisplayHook: Display.display() failed: " + t.getClass().getSimpleName()
                    + " - " + t.getMessage());
        }
        return item;
    }
}
