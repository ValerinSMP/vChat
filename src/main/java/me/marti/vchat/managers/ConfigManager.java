package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    private static final String LEGACY_MESSAGE_PREFIX =
            "<gray>[<#00FB9A>vChat</#00FB9A><gray>]</gray> ";

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
        String legacyNetworkId = "config.yml".equals(fileName) && !config.contains("redis.network-id")
                ? config.getString("redis.cluster-id") : null;

        // Fill any missing keys from the bundled default without touching existing values
        java.io.InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultStream, java.nio.charset.StandardCharsets.UTF_8));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
            if (legacyNetworkId != null && !legacyNetworkId.isBlank()) {
                config.set("redis.network-id", legacyNetworkId);
            }
            if ("messages.yml".equals(fileName)) {
                migrateLegacyMessagePrefix(config, defaults);
            }
            try { config.save(file); } catch (java.io.IOException e) {
                plugin.getLogger().warning("Could not save new defaults to " + fileName + ": " + e.getMessage());
            }
        }

        targetConfigs.put(fileName, config);
        targetFiles.put(fileName, file);
    }

    static boolean migrateLegacyMessagePrefix(FileConfiguration current, FileConfiguration defaults) {
        if (!LEGACY_MESSAGE_PREFIX.equals(current.getString("prefix"))) {
            return false;
        }
        current.set("prefix", defaults.getString("prefix"));
        return true;
    }

    public synchronized boolean reloadConfigs() {
        Map<String, FileConfiguration> candidates = new HashMap<>();
        try {
            for (String fileName : configs.keySet()) {
                File file = configFiles.get(fileName);
                YamlConfiguration candidate = new YamlConfiguration();
                candidate.load(file);
                InputStream defaultsStream = plugin.getResource(fileName);
                if (defaultsStream != null) {
                    FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(defaultsStream, java.nio.charset.StandardCharsets.UTF_8));
                    candidate.setDefaults(defaults);
                }
                candidates.put(fileName, candidate);
            }
        } catch (IOException | InvalidConfigurationException error) {
            plugin.getLogger().warning("Configuration reload rejected: " + error.getMessage());
            return false;
        }

        FileConfiguration oldMain = configs.get("config.yml");
        FileConfiguration newMain = candidates.get("config.yml");
        if (oldMain == null || newMain == null || immutableChanged(oldMain, newMain)) {
            plugin.getLogger().warning("Configuration reload rejected: storage/Redis/network/server-id changes require restart.");
            return false;
        }
        configs = Map.copyOf(candidates);
        plugin.getLogger().info("All configurations reloaded.");
        return true;
    }

    private static boolean immutableChanged(FileConfiguration oldConfig, FileConfiguration candidate) {
        String[] paths = {"storage.type", "storage.sqlite-file", "storage.mysql.host", "storage.mysql.port",
                "storage.mysql.database", "storage.mysql.user", "storage.mysql.password", "storage.mysql.use-ssl",
                "redis.enabled", "redis.host", "redis.port", "redis.user", "redis.password", "redis.database",
                "redis.use-ssl", "redis.network-id", "redis.server-id"};
        for (String path : paths) {
            if (!java.util.Objects.equals(oldConfig.get(path), candidate.get(path))) return true;
        }
        return false;
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
