package me.marti.vchat.commands;

import me.marti.vchat.VChat;
import me.marti.vchat.quiz.QuizManager;
import me.marti.vchat.quiz.QuizStats;
import me.marti.vchat.utils.PlatformUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QuizCommand implements CommandExecutor, TabCompleter {

    private final VChat plugin;
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();

    public QuizCommand(VChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        QuizManager quiz = plugin.getQuizManager();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";

        switch (sub) {
            case "start" -> {
                if (!sender.hasPermission("vchat.quiz.admin")) { noPerms(sender); return true; }
                if (quiz.isRunning()) { send(sender, "already-active"); return true; }
                quiz.start();
                send(sender, "started");
            }
            case "stop" -> {
                if (!sender.hasPermission("vchat.quiz.admin")) { noPerms(sender); return true; }
                if (!quiz.isRunning()) { send(sender, "not-active"); return true; }
                quiz.stop();
                send(sender, "stopped");
            }
            case "force" -> {
                if (!sender.hasPermission("vchat.quiz.admin")) { noPerms(sender); return true; }
                quiz.forceNext();
                send(sender, "forced");
            }
            case "reload" -> {
                if (!sender.hasPermission("vchat.quiz.admin")) { noPerms(sender); return true; }
                quiz.reload();
                send(sender, "reloaded");
            }
            case "stats" -> {
                if (!(sender instanceof Player player)) { send(sender, "players-only"); return true; }
                QuizStats stats = quiz.getStats();
                String prefix = msg("prefix");
                PlatformUtil.sendMessage(player, legacy.deserialize(msg("stats-header")));
                PlatformUtil.sendMessage(player, legacy.deserialize(prefix + msg("stats-correct")
                        .replace("{correct}", String.valueOf(stats.getCorrect(player.getUniqueId())))));
                PlatformUtil.sendMessage(player, legacy.deserialize(prefix + msg("stats-wrong")
                        .replace("{wrong}", String.valueOf(stats.getWrong(player.getUniqueId())))));
                PlatformUtil.sendMessage(player, legacy.deserialize(prefix + msg("stats-total")
                        .replace("{total}", String.valueOf(stats.getTotal(player.getUniqueId())))));
                PlatformUtil.sendMessage(player, legacy.deserialize(prefix + msg("stats-accuracy")
                        .replace("{accuracy}", stats.getAccuracy(player.getUniqueId()))));
            }
            case "top" -> {
                List<Map.Entry<String, Integer>> top = quiz.getStats().getTopPlayers(10);
                PlatformUtil.sendMessage(sender, legacy.deserialize(msg("top-header")));
                if (top.isEmpty()) {
                    PlatformUtil.sendMessage(sender, legacy.deserialize(msg("top-empty")));
                    return true;
                }
                String[] medals = {"&6🥇", "&7🥈", "&8&l🥉", "&7", "&7", "&7", "&7", "&7", "&7", "&7"};
                for (int i = 0; i < top.size(); i++) {
                    Map.Entry<String, Integer> entry = top.get(i);
                    String line = msg("top-entry")
                            .replace("{medal}", medals[Math.min(i, medals.length - 1)])
                            .replace("{name}", entry.getKey())
                            .replace("{correct}", String.valueOf(entry.getValue()))
                            .replace("{accuracy}", "?");
                    PlatformUtil.sendMessage(sender, legacy.deserialize(line));
                }
            }
            default -> {
                PlatformUtil.sendMessage(sender, legacy.deserialize(msg("help-header")));
                PlatformUtil.sendMessage(sender, legacy.deserialize("&7/quiz stats &8- &fVer tus estadísticas"));
                PlatformUtil.sendMessage(sender, legacy.deserialize("&7/quiz top &8- &fClasificación global"));
                if (sender.hasPermission("vchat.quiz.admin")) {
                    PlatformUtil.sendMessage(sender, legacy.deserialize("&7/quiz start &8- &fIniciar sistema"));
                    PlatformUtil.sendMessage(sender, legacy.deserialize("&7/quiz stop &8- &fDetener sistema"));
                    PlatformUtil.sendMessage(sender, legacy.deserialize("&7/quiz force &8- &fForzar siguiente pregunta"));
                    PlatformUtil.sendMessage(sender, legacy.deserialize("&7/quiz reload &8- &fRecargar config y preguntas"));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            boolean admin = sender.hasPermission("vchat.quiz.admin");
            if (admin) return List.of("start", "stop", "force", "reload", "stats", "top");
            return List.of("stats", "top");
        }
        return List.of();
    }

    private void send(CommandSender sender, String key) {
        PlatformUtil.sendMessage(sender, legacy.deserialize(msg("prefix") + msg(key)));
    }

    private void noPerms(CommandSender sender) {
        PlatformUtil.sendMessage(sender, legacy.deserialize("&cNo tienes permisos."));
    }

    private String msg(String key) {
        String value = plugin.getConfigManager().getConfig("quiz.yml").getString("messages." + key);
        return value != null ? value : key;
    }
}
