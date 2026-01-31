package me.marti.vchat.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class VChatTabCompleter implements TabCompleter {

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("vchat.admin") || sender.hasPermission("vchat.reload")) {
                suggestions.add("reload");
            }
            if (sender.hasPermission("vchat.admin") || sender.hasPermission("vchat.notify")) {
                suggestions.add("notify");
            }
            suggestions.add("mentions");
            suggestions.add("chat");
            suggestions.add("spy");
            suggestions.add("msg_toggle");
            suggestions.add("help");

            return filter(suggestions, args[0]);
        }

        return suggestions;
    }

    private List<String> filter(List<String> list, String input) {
        if (input == null || input.isEmpty())
            return list;
        List<String> filtered = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) {
                filtered.add(s);
            }
        }
        return filtered;
    }
}
