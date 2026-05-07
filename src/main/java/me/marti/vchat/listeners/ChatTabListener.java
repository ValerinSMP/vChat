package me.marti.vchat.listeners;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
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
        // If ProtocolLib injector is active, it owns mention suggestion behavior.
        if (plugin.isProtocolMentionsInjectorActive()) {
            return;
        }

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

            for (String playerName : plugin.getMentionManager().getOnlineMentionNamesStartingWith(prefix)) {
                completions.add("@" + playerName);
            }

            if (!completions.isEmpty()) {
                event.setCompletions(completions);
                event.setHandled(true);
            }
        }
    }
}
