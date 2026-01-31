package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    private final VChat plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> configFiles = new HashMap<>();

    public ConfigManager(VChat plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // List of all config files to manage
        String[] files = {
            "config.yml",
            "messages.yml",
            "filters.yml",
            "formats.yml",
            "mentions.yml",
            "private.yml"
        };

        for (String file : files) {
            registerConfig(file);
        }
    }

    private void registerConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        // Save default if not exists
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        // Load configuration
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        configs.put(fileName, config);
        configFiles.put(fileName, file);
    }

    public void reloadConfigs() {
        configs.clear();
        configFiles.clear();
        loadConfigs();
        plugin.getLogger().info("All configurations reloaded.");
    }

    public FileConfiguration getConfig(String fileName) {
        if (!configs.containsKey(fileName)) {
            // Lazy load or error? Let's try to load/register if missing
            registerConfig(fileName);
        }
        return configs.get(fileName);
    }
    
    // Convenience getters
    public FileConfiguration getMainConfig() { return getConfig("config.yml"); }
    public FileConfiguration getMessages() { return getConfig("messages.yml"); }
    public FileConfiguration getFilters() { return getConfig("filters.yml"); }
    public FileConfiguration getFormats() { return getConfig("formats.yml"); }
    public FileConfiguration getMentions() { return getConfig("mentions.yml"); }
    public FileConfiguration getPrivate() { return getConfig("private.yml"); }

    public void saveConfig(String fileName) {
        if (configFiles.containsKey(fileName) && configs.containsKey(fileName)) {
            try {
                configs.get(fileName).save(configFiles.get(fileName));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save config: " + fileName, e);
            }
        }
    }
}
