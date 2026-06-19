package me.marti.vchat.quiz;

import me.marti.vchat.VChat;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

public class QuizManager {

    private final VChat plugin;
    private final QuizStats stats;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    private List<QuizQuestion> questions = new ArrayList<>();
    private QuizQuestion currentQuestion;
    private final Set<UUID> answeredThisRound = new HashSet<>();
    private BukkitTask scheduleTask;
    private BukkitTask hintTask;
    private BukkitTask timeoutTask;
    private boolean running = false;
    private boolean questionActive = false;

    public QuizManager(VChat plugin) {
        this.plugin = plugin;
        this.stats = new QuizStats(plugin);
    }

    // ───────────────────────────────── lifecycle ─────────────────────────────

    public void start() {
        if (running) return;
        running = true;
        loadQuestions();
        scheduleNext();
    }

    public void stop() {
        running = false;
        cancelAll();
        currentQuestion = null;
        questionActive = false;
        answeredThisRound.clear();
    }

    public void reload() {
        stop();
        start();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isQuestionActive() {
        return questionActive;
    }

    // ───────────────────────────────── scheduling ─────────────────────────────

    private void scheduleNext() {
        if (!running) return;
        long intervalTicks = getConfig().getLong("quiz.interval", 300L) * 20L;
        scheduleTask = Bukkit.getScheduler().runTaskLater(plugin, this::launchQuestion, intervalTicks);
    }

    public void forceNext() {
        cancelAll();
        currentQuestion = null;
        questionActive = false;
        answeredThisRound.clear();
        launchQuestion();
    }

    private void launchQuestion() {
        if (!running) return;

        int minPlayers = getConfig().getInt("quiz.min-players", 1);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            scheduleNext();
            return;
        }

        if (questions.isEmpty()) {
            plugin.getLogger().warning("[Quiz] No hay preguntas cargadas.");
            scheduleNext();
            return;
        }

        currentQuestion = questions.get(new Random().nextInt(questions.size()));
        answeredThisRound.clear();
        questionActive = true;

        broadcastQuestion(currentQuestion);

        int answerTimeSecs = getConfig().getInt("quiz.answer-time", 30);
        boolean showHint = getConfig().getBoolean("quiz.show-hint", false);

        if (showHint) {
            long hintTicks = (answerTimeSecs / 2L) * 20L;
            hintTask = Bukkit.getScheduler().runTaskLater(plugin, this::broadcastHint, hintTicks);
        }

        timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, this::onTimeout, answerTimeSecs * 20L);
    }

    // ───────────────────────────────── answer handling ────────────────────────

    public void handleAnswer(Player player, String input) {
        if (!questionActive || currentQuestion == null) return;
        if (answeredThisRound.contains(player.getUniqueId())) {
            PlatformUtil.sendMessage(player, legacy.deserialize(msg("already-answered")));
            return;
        }

        if (currentQuestion.isCorrect(input)) {
            answeredThisRound.add(player.getUniqueId());
            onCorrect(player);
        }
        // Wrong answers are silent — no feedback to prevent give-aways
    }

    private void onCorrect(Player winner) {
        cancelHintAndTimeout();
        questionActive = false;

        stats.addCorrect(winner.getUniqueId(), winner.getName());

        String msg = msg("correct")
                .replace("{player}", winner.getName());
        broadcastRaw(msg);

        giveRewards(winner);
        scheduleNext();
    }

    private void onTimeout() {
        if (!questionActive) return;
        questionActive = false;
        if (currentQuestion == null) return;

        String msg = msg("timeout")
                .replace("{answer}", currentQuestion.answer());
        broadcastRaw(msg);

        scheduleNext();
    }

    // ───────────────────────────────── broadcast helpers ─────────────────────

    private void broadcastQuestion(QuizQuestion q) {
        String diffKey = "difficulty-" + q.difficulty();
        String diffLabel = msg(diffKey);
        if (diffLabel.equals(diffKey)) diffLabel = "&7[" + q.difficulty() + "]";

        List<String> opts = new ArrayList<>(q.options());
        opts.add(q.answer());
        Collections.shuffle(opts);
        String answersJoined = String.join(" &8| &a", opts);

        int answerTimeSecs = getConfig().getInt("quiz.answer-time", 30);

        List<String> lines = getConfig().getStringList("messages.question-format");
        for (String line : lines) {
            String rendered = line
                    .replace("{difficulty}", diffLabel)
                    .replace("{question}", q.question())
                    .replace("{answers}", answersJoined)
                    .replace("{time}", String.valueOf(answerTimeSecs));
            PlatformUtil.broadcast(legacy.deserialize(rendered));
        }
    }

    private void broadcastHint() {
        if (!questionActive || currentQuestion == null) return;
        String hintMsg = msg("hint")
                .replace("{hint}", currentQuestion.buildHint());
        broadcastRaw(hintMsg);
    }

    private void broadcastRaw(String legacyText) {
        String prefix = msg("prefix");
        PlatformUtil.broadcast(legacy.deserialize(prefix + legacyText));
    }

    // ───────────────────────────────── rewards ───────────────────────────────

    private void giveRewards(Player player) {
        FileConfiguration cfg = getConfig();
        if (!cfg.isList("rewards")) return;

        List<?> rewardList = cfg.getList("rewards");
        if (rewardList == null) return;

        List<String> givenLabels = new ArrayList<>();
        Random rng = new Random();

        Object economy = getVaultEconomy();

        for (Object obj : rewardList) {
            if (!(obj instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) raw;
            String type = String.valueOf(map.getOrDefault("type", "command"));
            int chance = Integer.parseInt(String.valueOf(map.getOrDefault("chance", "100")));
            if (rng.nextInt(100) >= chance) continue;

            String display = map.containsKey("display") ? String.valueOf(map.get("display")) : null;

            if ("command".equalsIgnoreCase(type)) {
                String cmd = String.valueOf(map.getOrDefault("value", ""));
                cmd = cmd.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                givenLabels.add(display != null ? display : "&7(comando)");
            } else if ("money".equalsIgnoreCase(type)) {
                double amount = Double.parseDouble(String.valueOf(map.getOrDefault("amount", "0")));
                if (economy != null) {
                    try {
                        economy.getClass().getMethod("depositPlayer",
                                org.bukkit.OfflinePlayer.class, double.class)
                                .invoke(economy, player, amount);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[Quiz] Error depositando dinero via Vault: " + e.getMessage());
                    }
                    givenLabels.add(display != null ? display : "&e$" + (int) amount);
                } else {
                    plugin.getLogger().warning("[Quiz] Vault Economy no disponible.");
                }
            } else if ("item".equalsIgnoreCase(type)) {
                String materialName = String.valueOf(map.getOrDefault("material", "DIRT"));
                int amount = Integer.parseInt(String.valueOf(map.getOrDefault("amount", "1")));
                Material mat = Material.matchMaterial(materialName);
                if (mat != null) {
                    player.getInventory().addItem(new ItemStack(mat, amount));
                    givenLabels.add(display != null ? display : "&f" + amount + "x " + materialName);
                }
            }
        }

        if (!givenLabels.isEmpty()) {
            String rewardMsg = msg("reward")
                    .replace("{rewards}", String.join("&7, ", givenLabels));
            PlatformUtil.sendMessage(player, legacy.deserialize(msg("prefix") + rewardMsg));
        }
    }

    private Object getVaultEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null;
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            org.bukkit.plugin.RegisteredServiceProvider<?> rsp =
                    Bukkit.getServicesManager().getRegistration(econClass);
            return rsp != null ? rsp.getProvider() : null;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    // ───────────────────────────────── utils ──────────────────────────────────

    public QuizStats getStats() {
        return stats;
    }

    private void loadQuestions() {
        questions.clear();
        File f = new File(plugin.getDataFolder(), "questions.txt");
        if (!f.exists()) {
            plugin.saveResource("questions.txt", false);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                QuizQuestion q = QuizQuestion.parse(line);
                if (q != null) questions.add(q);
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Error loading questions.txt", e);
        }
        plugin.getLogger().info("[Quiz] " + questions.size() + " preguntas cargadas.");
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("quiz.yml");
    }

    private String msg(String key) {
        String value = getConfig().getString("messages." + key);
        return value != null ? value : key;
    }

    private void cancelAll() {
        cancelHintAndTimeout();
        if (scheduleTask != null) {
            scheduleTask.cancel();
            scheduleTask = null;
        }
    }

    private void cancelHintAndTimeout() {
        if (hintTask != null) {
            hintTask.cancel();
            hintTask = null;
        }
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
    }
}
