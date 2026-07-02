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
    private volatile Map<String, FileConfiguration> configs = Map.of();
    private volatile Map<String, File> configFiles = Map.of();

    public ConfigManager(VChat plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        Map<String, FileConfiguration> loadedConfigs = new HashMap<>();
        Map<String, File> loadedFiles = new HashMap<>();

        // List of all config files to manage
        String[] files = {
            "config.yml",
            "messages.yml",
            "filters.yml",
            "formats.yml",
            "mentions.yml",
            "private.yml",
            "bridge.yml"
        };

        for (String file : files) {
            registerConfig(file, loadedConfigs, loadedFiles);
        }

        configs = Map.copyOf(loadedConfigs);
        configFiles = Map.copyOf(loadedFiles);
    }

    private void registerConfig(String fileName, Map<String, FileConfiguration> targetConfigs, Map<String, File> targetFiles) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        // Save default if not exists
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        // Load configuration
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Fill any missing keys from the bundled default without touching existing values
        java.io.InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            try { config.save(file); } catch (java.io.IOException e) {
                plugin.getLogger().warning("Could not save new defaults to " + fileName + ": " + e.getMessage());
            }
        }

        targetConfigs.put(fileName, config);
        targetFiles.put(fileName, file);
    }

    public void reloadConfigs() {
        loadConfigs();
        plugin.getLogger().info("All configurations reloaded.");
    }

    public FileConfiguration getConfig(String fileName) {
        FileConfiguration config = configs.get(fileName);
        if (config != null) {
            return config;
        }

        synchronized (this) {
            config = configs.get(fileName);
            if (config != null) {
                return config;
            }

            Map<String, FileConfiguration> updatedConfigs = new HashMap<>(configs);
            Map<String, File> updatedFiles = new HashMap<>(configFiles);
            registerConfig(fileName, updatedConfigs, updatedFiles);
            configs = Map.copyOf(updatedConfigs);
            configFiles = Map.copyOf(updatedFiles);
            return configs.get(fileName);
        }
    }
    
    // Convenience getters
    public FileConfiguration getMainConfig() { return getConfig("config.yml"); }
    public FileConfiguration getMessages() { return getConfig("messages.yml"); }
    public FileConfiguration getFilters() { return getConfig("filters.yml"); }
    public FileConfiguration getFormats() { return getConfig("formats.yml"); }
    public FileConfiguration getMentions() { return getConfig("mentions.yml"); }
    public FileConfiguration getPrivate() { return getConfig("private.yml"); }
    public FileConfiguration getBridge() { return getConfig("bridge.yml"); }

    public synchronized void saveConfig(String fileName) {
        File file = configFiles.get(fileName);
        FileConfiguration config = configs.get(fileName);
        if (file != null && config != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save config: " + fileName, e);
            }
        }
    }
}
