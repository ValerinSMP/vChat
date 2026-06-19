package me.marti.vchat.managers;

import me.clip.placeholderapi.PlaceholderAPI;
import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnouncementManager {

    private final VChat plugin;
    private int taskId = -1;
    private final LegacyComponentSerializer legacyAmp = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private record Announcement(String key, int interval, String sound, List<String> lines) {}

    private List<Announcement> announcements = new ArrayList<>();
    private int currentIndex = 0;
    private boolean random;
    private int globalInterval;

    public AnnouncementManager(VChat plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        FileConfiguration cfg = plugin.getConfigManager().getConfig("announcements.yml");

        if (!cfg.getBoolean("announcement.enabled", true)) return;

        random = cfg.getBoolean("announcement.random", true);
        globalInterval = cfg.getInt("announcement.interval", 600);

        announcements = loadAnnouncements(cfg);
        if (announcements.isEmpty()) return;

        if (random) Collections.shuffle(announcements);
        currentIndex = 0;

        scheduleNext();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    private void scheduleNext() {
        if (announcements.isEmpty()) return;

        Announcement next = announcements.get(currentIndex);
        int ticks = next.interval() * 20;

        taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcast(next);
            advance();
            scheduleNext();
        }, ticks).getTaskId();
    }

    private void advance() {
        currentIndex++;
        if (currentIndex >= announcements.size()) {
            currentIndex = 0;
            if (random) Collections.shuffle(announcements);
        }
    }

    private void broadcast(Announcement ann) {
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (online.isEmpty()) return;

        // Use a sample player for PAPI context (first online player)
        Player sample = online.get(0);

        for (String rawLine : ann.lines()) {
            String line = rawLine;
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                line = PlaceholderAPI.setPlaceholders(sample, line);
            }
            Component comp = legacyAmp.deserialize(line);
            for (Player p : online) {
                if (plugin.getAdminManager().isAnnouncementsMuted(p)) continue;
                PlatformUtil.sendMessage(p, comp);
            }
        }

        if (ann.sound() != null && !ann.sound().isBlank()) {
            org.bukkit.Sound sound = PlatformUtil.resolveSound(ann.sound());
            if (sound != null) {
                for (Player p : online) {
                    if (plugin.getAdminManager().isAnnouncementsMuted(p)) continue;
                    p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
                }
            }
        }
    }

    private List<Announcement> loadAnnouncements(FileConfiguration cfg) {
        List<Announcement> list = new ArrayList<>();
        ConfigurationSection section = cfg.getConfigurationSection("announcements");
        if (section == null) return list;

        for (String key : section.getKeys(false)) {
            ConfigurationSection ann = section.getConfigurationSection(key);
            if (ann == null) continue;

            int interval = ann.getInt("interval", globalInterval);
            String sound = ann.getString("sound", "");
            List<String> lines = ann.getStringList("lines");

            list.add(new Announcement(key, interval, sound, lines));
        }
        return list;
    }
}
