package me.marti.vchat.managers;

import me.marti.vchat.VChat;

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
    private Thread workerThread;

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
        workerThread = new Thread(this::runLoop, "vChat-LogWriter");
        workerThread.setDaemon(false);
        workerThread.start();
    }

    private void runLoop() {
        while (running || !logQueue.isEmpty()) {
            String log;
            try {
                log = logQueue.poll();
                if (log == null) {
                    Thread.sleep(10L);
                    continue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                continue;
            }

            try (FileWriter fw = new FileWriter(logFile, true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw)) {
                out.println(log);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not write chat log: " + e.getMessage());
            }
        }
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
        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
