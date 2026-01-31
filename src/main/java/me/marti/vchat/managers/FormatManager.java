package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

public class FormatManager {

    private final VChat plugin;
    private final LuckPerms luckPerms;

    public FormatManager(VChat plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    /**
     * Retrieves the raw format string for a player based on their primary group.
     */
    public String getFormat(Player player) {
        User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
        String group = user.getPrimaryGroup();

        // Check for group-specific format
        String format = plugin.getConfigManager().getFormats().getString("group-formats." + group);

        // Fallback to default format
        if (format == null) {
            format = plugin.getConfigManager().getFormats().getString("chat-format");
        }

        if (format == null) {
            return "<red>Error: Chat format not defined in formats.yml!</red>";
        }

        return format;
    }

    public void reload() {
        // Any cached data clearing would go here
    }
}
