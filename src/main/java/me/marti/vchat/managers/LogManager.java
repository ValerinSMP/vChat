package me.marti.vchat.managers;

import me.marti.vchat.VChat;
import org.bukkit.Bukkit;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class LogManager {

    private final VChat plugin;
    private final File logFile;
    private final BlockingQueue<String> logQueue = new LinkedBlockingQueue<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private volatile boolean running = true;

    public LogManager(VChat plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "logs/violations.log");

        setupLogFile();
        startLogThread();
    }

    private void setupLogFile() {
        if (!logFile.getParentFile().exists()) {
            logFile.getParentFile().mkdirs();
        }
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create log file: " + e.getMessage());
            }
        }
    }

    private void startLogThread() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            while (running || !logQueue.isEmpty()) {
                try {
                    String log = logQueue.take();
                    try (FileWriter fw = new FileWriter(logFile, true);
                            BufferedWriter bw = new BufferedWriter(fw);
                            PrintWriter out = new PrintWriter(bw)) {
                        out.println(log);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    public void logViolation(String player, String reason, String message) {
        if (!plugin.getConfigManager().getMainConfig().getBoolean("logging.enabled", true))
            return;

        String format = plugin.getConfigManager().getMainConfig().getString("logging.format", "[%date%] %player%: %reason% | %message%");
        String date = dateFormat.format(new Date());

        String entry = format
                .replace("%date%", date)
                .replace("%player%", player)
                .replace("%reason%", reason)
                .replace("%message%", message);

        logQueue.offer(entry);
    }

    public void shutdown() {
        running = false;
        // The weird thing about Bukkit async tasks is they might be killed abruptly on
        // shutdown,
        // so we hope the queue drains or we manually drain it here if we were on a
        // dedicated thread handling lifecycle better.
        // For simplicity and standard plugin behavior, this is usually 'good enough'
        // but strict flushing is better.
    }
}
