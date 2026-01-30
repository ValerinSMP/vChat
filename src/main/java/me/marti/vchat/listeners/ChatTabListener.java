package me.marti.vchat.listeners;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import me.marti.vchat.VChat;
import java.util.List;
import java.util.ArrayList;

public class ChatTabListener implements Listener {

    private final VChat plugin;

    public ChatTabListener(VChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onTabComplete(AsyncTabCompleteEvent event) {
        String buffer = event.getBuffer();
        if (buffer.startsWith("/"))
            return;

        // Get last token logic
        String lastToken = buffer;
        int lastSpace = buffer.lastIndexOf(' ');
        if (lastSpace != -1) {
            lastToken = buffer.substring(lastSpace + 1);
        }

        if (lastToken.startsWith("@")) {
            String prefix = lastToken.substring(1).toLowerCase();
            List<String> completions = new ArrayList<>();

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(prefix)) {
                    completions.add("@" + p.getName());
                }
            }

            if (!completions.isEmpty()) {
                event.setCompletions(completions);
                event.setHandled(true);
            }
        }
    }
}
