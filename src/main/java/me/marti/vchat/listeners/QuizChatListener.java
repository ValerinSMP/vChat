package me.marti.vchat.listeners;

import me.marti.vchat.VChat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation")
public class QuizChatListener implements Listener {

    private final VChat plugin;

    public QuizChatListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getQuizManager() == null) return;
        if (!plugin.getQuizManager().isQuestionActive()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        plugin.getServer().getScheduler().runTask(plugin,
                () -> plugin.getQuizManager().handleAnswer(player, message));
    }
}
