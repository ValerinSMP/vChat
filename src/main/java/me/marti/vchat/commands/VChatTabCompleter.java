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
        boolean adminRoot = command.getName().equalsIgnoreCase("vchatadmin");

        if (args.length == 1) {
            suggestions.add("help");
            suggestions.add("about");
            if (adminRoot) {
                if (sender.hasPermission("vchat.admin") || sender.hasPermission("vchat.reload")) {
                    suggestions.add("reload");
                }
                if (sender.hasPermission("vchat.admin") || sender.hasPermission("vchat.notify")) {
                    suggestions.add("notify");
                }
                if (sender.hasPermission("vchat.admin") || sender.hasPermission("vchat.spychat")) {
                    suggestions.add("spy");
                }
                if (sender.hasPermission("vchat.bridge.admin") || sender.hasPermission("vchat.admin")) {
                    suggestions.add("bridge");
                }
                if (sender.hasPermission("vchat.debug") || sender.hasPermission("vchat.admin")) {
                    suggestions.add("debug");
                }
            } else {
                suggestions.add("mentions");
                suggestions.add("chat");
                suggestions.add("msg_toggle");
                suggestions.add("toggledeath");
            }

            return filter(suggestions, args[0]);
        }

        if (adminRoot && args.length == 2 && args[0].equalsIgnoreCase("bridge")
                && (sender.hasPermission("vchat.bridge.admin") || sender.hasPermission("vchat.admin"))) {
            suggestions.add("status");
            suggestions.add("reload");
            suggestions.add("block");
            suggestions.add("unblock");
            suggestions.add("list");
            suggestions.add("test");
            return filter(suggestions, args[1]);
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
