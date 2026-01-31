package me.marti.vchat;

import me.marti.vchat.commands.ReloadCommand;
import me.marti.vchat.listeners.ChatListener;
import me.marti.vchat.managers.FormatManager;
import me.marti.vchat.processors.MessageProcessor;
import net.luckperms.api.LuckPerms;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class VChat extends JavaPlugin {

    private me.marti.vchat.managers.ConfigManager configManager;
    private FormatManager formatManager;
    private MessageProcessor messageProcessor;
    private me.marti.vchat.managers.ItemViewManager itemViewManager;
    private me.marti.vchat.managers.FilterManager filterManager;
    private me.marti.vchat.managers.AdminManager adminManager;
    private me.marti.vchat.managers.LogManager logManager;
    private me.marti.vchat.managers.MentionManager mentionManager;
    private me.marti.vchat.managers.PrivateMessageManager privateMessageManager;
    private me.marti.vchat.managers.IgnoreManager ignoreManager;
    private me.marti.vchat.compat.MentionsTabInjector mentionsTabInjector;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        // Dependency Check
        if (!setupLuckPerms()) {
            getLogger().severe("LuckPerms not found! Disabling vChat.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Managers
        this.configManager = new me.marti.vchat.managers.ConfigManager(this);
        this.configManager.loadConfigs();

        this.logManager = new me.marti.vchat.managers.LogManager(this);
        this.adminManager = new me.marti.vchat.managers.AdminManager(this);
        this.mentionManager = new me.marti.vchat.managers.MentionManager(this);
        this.filterManager = new me.marti.vchat.managers.FilterManager(this);
        this.itemViewManager = new me.marti.vchat.managers.ItemViewManager(this);
        this.formatManager = new FormatManager(this, luckPerms);
        this.messageProcessor = new MessageProcessor(this, luckPerms);
        this.privateMessageManager = new me.marti.vchat.managers.PrivateMessageManager(this);
        this.ignoreManager = new me.marti.vchat.managers.IgnoreManager(this);

        // Register Commands (Deferred by 1 tick to prevent
        // ConcurrentModificationException with Paper's Async Command Builder during
        // reloads)
        getServer().getScheduler().runTaskLater(this, () -> {
            getCommand("vchat").setExecutor(
                    new me.marti.vchat.commands.VChatCommand(this, itemViewManager, adminManager));
            getCommand("vchat").setTabCompleter(new me.marti.vchat.commands.VChatTabCompleter());

            getCommand("showitem").setExecutor(
                    new me.marti.vchat.commands.ShowItemCommand(this, messageProcessor, luckPerms));

            getCommand("msg").setExecutor(new me.marti.vchat.commands.PrivateMessageCommand(this));
            getCommand("reply").setExecutor(new me.marti.vchat.commands.ReplyCommand(this));
            getCommand("togglemsg").setExecutor(new me.marti.vchat.commands.ToggleMsgCommand(this));
            getCommand("spychat").setExecutor(new me.marti.vchat.commands.SocialSpyCommand(this));

            getCommand("togglechat").setExecutor(new me.marti.vchat.commands.ToggleChatCommand(this));
            getCommand("mutechat").setExecutor(new me.marti.vchat.commands.MuteChatCommand(this));

            getCommand("ignore").setExecutor(new me.marti.vchat.commands.IgnoreCommand(this));
            getCommand("togglementions").setExecutor(new me.marti.vchat.commands.ToggleMentionsCommand(this));

            getLogger().info("Commands registered successfully.");
        }, 1L);

        // Register Listeners
        getServer().getPluginManager().registerEvents(
                new ChatListener(this, formatManager, messageProcessor, filterManager, mentionManager),
                this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.ChatTabListener(this), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.QuitListener(this), this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new me.marti.vchat.placeholders.PAPIExpansion(this).register();
        }

        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            getLogger().info("Hooking into ProtocolLib for Tab Completion...");
            this.mentionsTabInjector = new me.marti.vchat.compat.MentionsTabInjector(this);
            this.mentionsTabInjector.register();
        }

        printStartupBanner();
    }

    public me.marti.vchat.managers.ConfigManager getConfigManager() {
        return configManager;
    }

    public me.marti.vchat.managers.PrivateMessageManager getPrivateMessageManager() {
        return privateMessageManager;
    }

    public me.marti.vchat.managers.IgnoreManager getIgnoreManager() {
        return ignoreManager;
    }

    public me.marti.vchat.managers.MentionManager getMentionManager() {
        return mentionManager;
    }

    public me.marti.vchat.managers.ItemViewManager getItemViewManager() {
        return itemViewManager;
    }

    public me.marti.vchat.managers.AdminManager getAdminManager() {
        return adminManager;
    }

    public me.marti.vchat.managers.FilterManager getFilterManager() {
        return filterManager;
    }

    public me.marti.vchat.managers.LogManager getLogManager() {
        return logManager;
    }

    @Override
    public void onDisable() {
        // Cancel all async/sync tasks
        getServer().getScheduler().cancelTasks(this);

        // Unregister ProtocolLib Hook if exists (Optional, but good practice)
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            com.comphenix.protocol.ProtocolLibrary.getProtocolManager().removePacketListeners(this);
        }

        if (mentionsTabInjector != null) {
            mentionsTabInjector.shutdown();
        }

        getLogger().info("vChat has been disabled!");
    }

    private void printStartupBanner() {
        boolean papi = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        String version = getDescription().getVersion();

        getServer().getConsoleSender().sendMessage(net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.text("  vChat v" + version,
                        net.kyori.adventure.text.format.NamedTextColor.AQUA))
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.text("  Developed by Marti",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.newline())
                .append(net.kyori.adventure.text.Component.text("  Modules: ",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY))
                .append(net.kyori.adventure.text.Component
                        .text("LuckPerms ",
                                net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        .append(net.kyori.adventure.text.Component.text("✔",
                                net.kyori.adventure.text.format.NamedTextColor.GREEN)))
                .append(net.kyori.adventure.text.Component.text(" | ",
                        net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY))
                .append(net.kyori.adventure.text.Component
                        .text("PAPI ",
                                papi ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                                        : net.kyori.adventure.text.format.NamedTextColor.RED)
                        .append(net.kyori.adventure.text.Component.text(papi ? "✔" : "✖",
                                papi ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                                        : net.kyori.adventure.text.format.NamedTextColor.RED)))
                .append(net.kyori.adventure.text.Component.newline())
                .build());
        getLogger().info("vChat has been enabled successfully!");
    }

    private boolean setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager()
                .getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
            return true;
        }
        return false;
    }

    public void reload() {
        configManager.reloadConfigs();
        formatManager.reload();
        if (filterManager != null) {
            filterManager.loadFilters();
        }
        getLogger().info("Configuration reloaded.");
    }
}
