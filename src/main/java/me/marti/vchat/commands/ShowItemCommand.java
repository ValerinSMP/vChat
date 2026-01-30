package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import me.marti.vchat.processors.MessageProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class ShowItemCommand implements CommandExecutor {

    private final VChat plugin;
    private final MessageProcessor messageProcessor;
    private final net.luckperms.api.LuckPerms luckPerms;

    public ShowItemCommand(VChat plugin, MessageProcessor messageProcessor, net.luckperms.api.LuckPerms luckPerms) {
        this.plugin = plugin;
        this.messageProcessor = messageProcessor;
        this.luckPerms = luckPerms;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("vchat.showitem")) {
            player.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            String errorMsg = plugin.getConfig().getString("show-item-error",
                    "<red>You must be holding an item to show it.</red>");
            player.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(errorMsg));
            return true;
        }

        // Get format
        String format = plugin.getConfig().getString("show-item-message",
                "{prefix}<gray>{name}</gray> <white>is holding</white> {item}");

        // 0. Get LuckPerms Data (Same logic as MessageProcessor)
        net.luckperms.api.cacheddata.CachedMetaData metaData = this.luckPerms.getPlayerAdapter(Player.class)
                .getMetaData(player);
        String prefix = metaData.getPrefix() != null ? metaData.getPrefix() : "";
        String suffix = metaData.getSuffix() != null ? metaData.getSuffix() : "";

        // 1. Process string placeholders (PAPI mostly)
        format = format.replace("{prefix}", prefix)
                .replace("{suffix}", suffix);

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            format = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, format);
        }

        // 2. Replace {name} and {prefix} manually (rudimentary)
        // Since we don't have easy access to LuckPerms logic here without duplicating
        // code or exposing it
        // We rely on PAPI for {prefix} usually, but let's do simple name replacement
        format = format.replace("{name}", player.getName())
                .replace("{displayname}", player.getDisplayName())
                .replace("{item}", "<item>");

        // 3. Item Component
        Component itemComponent = messageProcessor.getItemComponent(item);

        // 4. Build Component
        // We assume the user config is MiniMessage friendly for now.
        Component finalMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize(format, Placeholder.component("item", itemComponent));

        // Broadcast
        Bukkit.getServer().sendMessage(finalMessage);

        return true;
    }
}
