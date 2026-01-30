package me.marti.vchat.compat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import me.marti.vchat.VChat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ProtocolLibHook {

    private final VChat plugin;

    public ProtocolLibHook(VChat plugin) {
        this.plugin = plugin;
    }

    public void register() {
        // Handle Client -> Server Request
        ProtocolLibrary.getProtocolManager()
                .addPacketListener(new PacketAdapter(plugin, com.comphenix.protocol.events.ListenerPriority.HIGHEST,
                        PacketType.Play.Client.TAB_COMPLETE) {
                    @Override
                    public void onPacketReceiving(PacketEvent event) {
                        if (event.getPlayer() == null)
                            return;

                        // Read packet content
                        PacketContainer packet = event.getPacket();
                        String text = "";

                        plugin.getLogger().info("[DEBUG] Raw Packet Received: " + packet.getType());

                        try {
                            // ProtocolLib handles version diffs, usually index 0 is the text
                            text = packet.getStrings().read(0);
                        } catch (Exception e) {
                            plugin.getLogger().severe("[DEBUG] Failed to read packet string: " + e.getMessage());
                            return;
                        }

                        // Debug to confirm we even get it
                        plugin.getLogger().info("Packet Tab Received: " + text);

                        int lastSpace = text.lastIndexOf(' ');
                        String lastWord = lastSpace == -1 ? text : text.substring(lastSpace + 1);

                        // Trigger if:
                        // 1. Starts with @ (Explicit mention)
                        // 2. Or standard text (Implicit mention suggestion)
                        // 3. Or empty (Show all)
                        // But NOT if it's a command (starts with /) - handled by text check earlier if
                        // needed?
                        // text is the full buffer. If it starts with /, usually client handles it or
                        // sends COMMAND_TAB packet?
                        // Actually TAB_COMPLETE (legacy) sends / for commands too in 1.12-, but 1.13+
                        // use specific packets.
                        // Assuming this is chat:

                        if (!text.startsWith("/")) {
                            plugin.getLogger()
                                    .info("[DEBUG] ProtocolLib Hook: Packet Received for text: '" + text + "'");
                            // We allow empty lastWord (global list) or any partial
                            List<String> matches = getMentionCompletions(event.getPlayer(), lastWord);
                            if (!matches.isEmpty()) {
                                // Stop vanilla from sending its own list (which overwrites/races ours)
                                event.setCancelled(true);

                                try {
                                    sendTabCompleteResponse(event.getPlayer(), event.getPacket().getIntegers().read(0),
                                            text, matches);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                });
    }

    // Construct and send the detailed response packet
    private void sendTabCompleteResponse(Player player, int transactionId, String text, List<String> matches) {
        // This part is tricky in 1.21 because of the Brigadier Suggestions object
        // structure.
        // Typically, we construct a new packet of PacketType.Play.Server.TAB_COMPLETE

        PacketContainer response = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.TAB_COMPLETE);
        response.getIntegers().write(0, transactionId); // Transaction ID matters in 1.13+

        // Using SuggestionsBuilder to generate the specific range
        int start = text.lastIndexOf(' ') + 1;
        int length = text.length() - start;

        SuggestionsBuilder builder = new SuggestionsBuilder(text, start);
        for (String match : matches) {
            builder.suggest(match);
        }
        Suggestions suggestions = builder.build();

        // ProtocolLib *might* need us to wrap this into the specific NMS object or use
        // a modifier.
        // In ProtocolLib 5.0+, we might be able to write the Suggestions object
        // directly if supported,
        // OR specifically set Start, Length, and String List.
        // Modern ProtocolLib logic:

        response.getModifier().write(0, suggestions);

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> getMentionCompletions(Player player, String partial) {
        if (!plugin.getConfig().getBoolean("mentions.enabled"))
            return Collections.emptyList();

        List<String> results = new java.util.ArrayList<>();
        String lowerPartial = partial.toLowerCase();

        // If partial starts with @, strip it for matching names, but result will
        // strictly be @Name
        boolean explicitlyMentioning = partial.startsWith("@");
        String prefix = explicitlyMentioning ? lowerPartial.substring(1) : lowerPartial;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!player.canSee(p))
                continue;
            String name = p.getName();
            String lowerName = name.toLowerCase();

            // Match Logic
            if (lowerName.startsWith(prefix)) {
                // Add plain name ONLY if we are NOT explicitly mentioning (to mimic vanilla
                // behavior the user asked for)
                // The user said: "ademas de el tipi nombre nomas de tab que sale con el
                // vanilla"
                // This implies if I type nothing or "Na", I want "Name" AND "@Name".
                // But if I type "@Na", I probably only want "@Name".
                if (!explicitlyMentioning) {
                    results.add(name);
                }

                // Add @Name (Exclude self from mentions logic, as per user request to block
                // self-mention)
                if (!p.equals(player)) {
                    results.add("@" + name);
                }
            }
        }

        Collections.sort(results);
        return results;
    }
}
