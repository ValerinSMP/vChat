package me.marti.vchat.quiz;

import me.marti.vchat.VChat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public class QuizStats {

    private final VChat plugin;
    private final File file;
    private FileConfiguration data;

    public QuizStats(VChat plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quiz_stats.yml");
        load();
    }

    private void load() {
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void addCorrect(UUID uuid, String name) {
        String path = "players." + uuid;
        data.set(path + ".name", name);
        data.set(path + ".correct", data.getInt(path + ".correct", 0) + 1);
        data.set(path + ".total", data.getInt(path + ".total", 0) + 1);
        save();
    }

    public void addWrong(UUID uuid, String name) {
        String path = "players." + uuid;
        data.set(path + ".name", name);
        data.set(path + ".wrong", data.getInt(path + ".wrong", 0) + 1);
        data.set(path + ".total", data.getInt(path + ".total", 0) + 1);
        save();
    }

    public int getCorrect(UUID uuid) {
        return data.getInt("players." + uuid + ".correct", 0);
    }

    public int getWrong(UUID uuid) {
        return data.getInt("players." + uuid + ".wrong", 0);
    }

    public int getTotal(UUID uuid) {
        return data.getInt("players." + uuid + ".total", 0);
    }

    public String getAccuracy(UUID uuid) {
        int total = getTotal(uuid);
        if (total == 0) return "0.0";
        double acc = (getCorrect(uuid) * 100.0) / total;
        return String.format(Locale.US, "%.1f", acc);
    }

    public List<Map.Entry<String, Integer>> getTopPlayers(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>();
        if (!data.isConfigurationSection("players")) return list;
        for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
            String name = data.getString("players." + uuidStr + ".name", uuidStr);
            int correct = data.getInt("players." + uuidStr + ".correct", 0);
            list.add(Map.entry(name, correct));
        }
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save quiz_stats.yml", e);
        }
    }
}
