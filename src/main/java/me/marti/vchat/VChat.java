package me.marti.vchat;

import me.marti.vchat.managers.FormatManager;
import me.marti.vchat.processors.MessageProcessor;
import net.luckperms.api.LuckPerms;
import org.bukkit.event.Listener;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

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
    private me.marti.vchat.managers.DiscordBridgeManager discordBridgeManager;
    private me.marti.vchat.managers.JoinQuitManager joinQuitManager;
    private me.marti.vchat.compat.MentionsTabInjector mentionsTabInjector;
    private me.marti.vchat.compat.NexoHook nexoHook;
    private me.marti.vchat.compat.EcoDisplayHook ecoDisplayHook;
    private me.marti.vchat.redis.RedisManager redisManager;
    private me.marti.vchat.redis.RedisEventHandler redisEventHandler;
    private me.marti.vchat.storage.StorageManager storageManager;
    private java.util.concurrent.ExecutorService ioExecutor;
    private int itemCacheCleanupTaskId = -1;
    private int redisHeartbeatTaskId = -1;
    private volatile boolean debugMode;
    private LuckPerms luckPerms;

    @Override
    public void onEnable() {
        long startedAt = System.nanoTime();
        getLogger().info("Starting vChat v" + getDescription().getVersion() + "...");
        getLogger().info("Platform: Paper 1.21.11+ | Java 21 bytecode");

        // Dependency Check
        if (!setupLuckPerms()) {
            getLogger().severe("LuckPerms not found! Disabling vChat.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Managers
        this.configManager = new me.marti.vchat.managers.ConfigManager(this);
        this.configManager.loadConfigs();
        this.ioExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "vchat:io");
            thread.setDaemon(true);
            return thread;
        });
        this.storageManager = new me.marti.vchat.storage.StorageManager(this, ioExecutor);

        this.logManager = new me.marti.vchat.managers.LogManager(this);
        this.adminManager = new me.marti.vchat.managers.AdminManager(this);
        this.mentionManager = new me.marti.vchat.managers.MentionManager(this);
        this.filterManager = new me.marti.vchat.managers.FilterManager(this);
        this.itemViewManager = new me.marti.vchat.managers.ItemViewManager(this);
        this.formatManager = new FormatManager(this, luckPerms);
        this.messageProcessor = new MessageProcessor(this, luckPerms);
        this.privateMessageManager = new me.marti.vchat.managers.PrivateMessageManager(this);
        this.ignoreManager = new me.marti.vchat.managers.IgnoreManager(this);
        this.discordBridgeManager = new me.marti.vchat.managers.DiscordBridgeManager(this);
        this.joinQuitManager = new me.marti.vchat.managers.JoinQuitManager(this);
        this.redisEventHandler = new me.marti.vchat.redis.RedisEventHandler(this);
        this.redisManager = new me.marti.vchat.redis.RedisManager(this, ioExecutor);
        startStorageAndNetwork();

        // Register Commands
        registerCommands();

        // Register Listeners
        getServer().getPluginManager().registerEvents(createChatListener(), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.InventoryListener(), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.DeathListener(this), this);
        getServer().getPluginManager().registerEvents(createChatTabListener(), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.QuitListener(this), this);
        getServer().getPluginManager().registerEvents(new me.marti.vchat.listeners.CommandCooldownListener(this), this);

        // Initialize any players already online when the plugin is enabled.
        for (org.bukkit.entity.Player online : getServer().getOnlinePlayers()) {
            adminManager.loadData(online);
            mentionManager.loadData(online);
            privateMessageManager.loadData(online);
            ignoreManager.loadData(online);
            loadPlayerState(online);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new me.marti.vchat.placeholders.PAPIExpansion(this).register();
        }

        if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            getLogger().info("Hooking into ProtocolLib for Tab Completion...");
            this.mentionsTabInjector = new me.marti.vchat.compat.MentionsTabInjector(this);
            this.mentionsTabInjector.register();
        }

        if (getServer().getPluginManager().getPlugin("Nexo") != null) {
            getLogger().info("Hooking into Nexo for custom item name resolution...");
            this.nexoHook = new me.marti.vchat.compat.NexoHook(this);
        }

        // "eco" (Auxilor's EcoItems/EcoEnchants/EcoArmor framework) isn't its own listed
        // plugin — it's shaded into each of those products. Detect via reflection instead
        // of a plugin name check.
        this.ecoDisplayHook = new me.marti.vchat.compat.EcoDisplayHook(this);
        if (ecoDisplayHook.isAvailable()) {
            getLogger().info("Hooking into eco (EcoItems/EcoEnchants/EcoArmor) for dynamic item lore rendering...");
        }

        startBackgroundMaintenanceTasks();
        discordBridgeManager.start();

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        getLogger().info("Enabled successfully in " + elapsedMs + " ms.");
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

    public me.marti.vchat.managers.DiscordBridgeManager getDiscordBridgeManager() {
        return discordBridgeManager;
    }

    public me.marti.vchat.managers.JoinQuitManager getJoinQuitManager() {
        return joinQuitManager;
    }

    public me.marti.vchat.redis.RedisManager getRedisManager() {
        return redisManager;
    }

    public me.marti.vchat.redis.RedisEventHandler getRedisEventHandler() {
        return redisEventHandler;
    }

    public me.marti.vchat.storage.StorageManager getStorageManager() {
        return storageManager;
    }

    public net.luckperms.api.LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public me.marti.vchat.compat.NexoHook getNexoHook() {
        return nexoHook;
    }

    public me.marti.vchat.compat.EcoDisplayHook getEcoDisplayHook() {
        return ecoDisplayHook;
    }

    public boolean isProtocolMentionsInjectorActive() {
        return mentionsTabInjector != null;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public boolean toggleDebugMode() {
        debugMode = !debugMode;
        return debugMode;
    }

    public void debugLog(String message) {
        if (debugMode) {
            getLogger().info("[Debug] " + message);
        }
    }

    @Override
    public void onDisable() {
        long startedAt = System.nanoTime();
        getLogger().info("Stopping vChat...");
        if (redisManager != null) {
            redisManager.disable();
        }

        if (logManager != null) {
            logManager.shutdown();
        }

        // Unregister ProtocolLib Hook if exists (Optional, but good practice)
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            com.comphenix.protocol.ProtocolLibrary.getProtocolManager().removePacketListeners(this);
        }

        if (mentionsTabInjector != null) {
            mentionsTabInjector.shutdown();
        }
        if (discordBridgeManager != null) {
            discordBridgeManager.shutdown();
        }

        if (nexoHook != null) {
            nexoHook.shutdown();
            nexoHook = null;
        }

        stopBackgroundMaintenanceTasks();

        if (itemViewManager != null) {
            itemViewManager.clear();
        }

        // Cancel all remaining async/sync tasks
        getServer().getScheduler().cancelTasks(this);
        if (ioExecutor != null) {
            ioExecutor.shutdownNow();
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        getLogger().info("Disabled successfully in " + elapsedMs + " ms.");
    }

    private void registerCommands() {
        registerCommand("vchat", cmd -> {
            cmd.setExecutor(new me.marti.vchat.commands.VChatCommand(this, itemViewManager, adminManager));
            cmd.setTabCompleter(new me.marti.vchat.commands.VChatTabCompleter());
        });
        registerCommand("vchatadmin", cmd -> {
            cmd.setExecutor(new me.marti.vchat.commands.VChatCommand(this, itemViewManager, adminManager));
            cmd.setTabCompleter(new me.marti.vchat.commands.VChatTabCompleter());
        });

        registerCommand("showitem", cmd -> cmd.setExecutor(
                new me.marti.vchat.commands.ShowItemCommand(this, messageProcessor, luckPerms)));

        registerCommand("msg", cmd -> cmd.setExecutor(new me.marti.vchat.commands.PrivateMessageCommand(this)));
        registerCommand("reply", cmd -> cmd.setExecutor(new me.marti.vchat.commands.ReplyCommand(this)));
        registerCommand("togglemsg", cmd -> cmd.setExecutor(new me.marti.vchat.commands.ToggleMsgCommand(this)));
        registerCommand("spychat", cmd -> cmd.setExecutor(new me.marti.vchat.commands.SocialSpyCommand(this)));

        registerCommand("togglechat", cmd -> cmd.setExecutor(new me.marti.vchat.commands.ToggleChatCommand(this)));
        registerCommand("mutechat", cmd -> cmd.setExecutor(new me.marti.vchat.commands.MuteChatCommand(this)));

        registerCommand("ignore", cmd -> cmd.setExecutor(new me.marti.vchat.commands.IgnoreCommand(this)));
        registerCommand("togglementions", cmd -> cmd.setExecutor(new me.marti.vchat.commands.ToggleMentionsCommand(this)));
        getLogger().info("Commands registered successfully.");
    }

    private void registerCommand(String name, java.util.function.Consumer<org.bukkit.command.PluginCommand> binder) {
        org.bukkit.command.PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' not found in plugin.yml. Skipping binding.");
            return;
        }
        binder.accept(command);
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

    public boolean reload() {
        if (!configManager.reloadConfigs()) return false;
        formatManager.reload();
        if (filterManager != null) {
            filterManager.loadFilters();
        }
        if (discordBridgeManager != null) {
            discordBridgeManager.reload();
        }
        getLogger().info("Configuration reloaded.");
        return true;
    }

    private void startBackgroundMaintenanceTasks() {
        stopBackgroundMaintenanceTasks();

        long periodTicks = Math.max(20L, getConfigManager().getMainConfig().getLong("item-view.cleanup-interval-seconds", 120L) * 20L);
        itemCacheCleanupTaskId = getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (itemViewManager != null) {
                itemViewManager.purgeExpired();
            }
        }, periodTicks, periodTicks).getTaskId();

        long heartbeatTicks = Math.max(20L, getConfigManager().getMainConfig().getLong("redis.heartbeat-seconds", 10L) * 20L);
        redisHeartbeatTaskId = getServer().getScheduler().runTaskTimer(this, () -> {
            if (redisManager != null) redisManager.heartbeat();
        }, heartbeatTicks, heartbeatTicks).getTaskId();
    }

    private void stopBackgroundMaintenanceTasks() {
        if (itemCacheCleanupTaskId != -1) {
            getServer().getScheduler().cancelTask(itemCacheCleanupTaskId);
            itemCacheCleanupTaskId = -1;
        }
        if (redisHeartbeatTaskId != -1) {
            getServer().getScheduler().cancelTask(redisHeartbeatTaskId);
            redisHeartbeatTaskId = -1;
        }
    }

    private void startStorageAndNetwork() {
        storageManager.ready().whenComplete((ignored, error) -> {
            if (error != null) {
                getLogger().severe("Storage startup failed: " + rootMessage(error));
                return;
            }
            storageManager.loadGlobalMute().thenAccept(muted -> runMain(() -> adminManager.setGlobalChatMuted(muted)))
                    .exceptionally(loadError -> { getLogger().warning("Could not load global mute: " + rootMessage(loadError)); return null; });
            redisManager.enable().thenAccept(connected -> {
                if (!connected) return;
                runMain(() -> {
                    for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
                        loadPlayerState(player, () -> redisManager.registerPresence(
                                player.getUniqueId(), player.getName(), already -> { }));
                    }
                });
            });
        });
    }

    public void loadPlayerState(org.bukkit.entity.Player player) {
        loadPlayerState(player, () -> { });
    }

    public void loadPlayerState(org.bukkit.entity.Player player, Runnable afterLoad) {
        String playerName = player.getName();
        java.util.UUID playerId = player.getUniqueId();
        me.marti.vchat.storage.PlayerState legacy = snapshotPlayerState(player);
        storageManager.loadOrMigrate(playerId, playerName, legacy)
                .thenAccept(state -> runMain(() -> {
                    org.bukkit.entity.Player current = getServer().getPlayer(playerId);
                    if (current != null && current.isOnline()) {
                        applyPlayerState(current, state);
                        afterLoad.run();
                    }
                }))
                .exceptionally(error -> { getLogger().warning("Could not load durable state for " + playerName + ": " + rootMessage(error)); return null; });
    }

    public void savePlayerState(org.bukkit.entity.Player player) {
        java.util.UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        me.marti.vchat.storage.PlayerState state = snapshotPlayerState(player);
        storageManager.save(playerId, playerName, state).thenRun(() -> {
            if (redisManager != null && redisManager.isEnabled()) {
                redisManager.publish(new me.marti.vchat.redis.RedisEvent(me.marti.vchat.redis.RedisEventType.PREFERENCE_INVALIDATE)
                        .put("playerUuid", playerId.toString()));
            }
        }).exceptionally(error -> { getLogger().warning("Could not persist state for " + playerName + ": " + rootMessage(error)); return null; });
    }

    public void reloadPlayerState(java.util.UUID playerId) {
        storageManager.load(playerId).thenAccept(state -> runMain(() -> {
            org.bukkit.entity.Player player = getServer().getPlayer(playerId);
            if (player != null && player.isOnline()) applyPlayerState(player, state);
        })).exceptionally(error -> { getLogger().warning("Could not refresh player state: " + rootMessage(error)); return null; });
    }

    private me.marti.vchat.storage.PlayerState snapshotPlayerState(org.bukkit.entity.Player player) {
        return new me.marti.vchat.storage.PlayerState(
                privateMessageManager.isMsgEnabled(player), privateMessageManager.isSpyEnabled(player),
                adminManager.isPersonalChatMuted(player), mentionManager.areMentionsEnabled(player),
                adminManager.isDeathMuted(player), adminManager.isNotifyEnabled(player),
                ignoreManager.getIgnoredPlayers(player));
    }

    private void applyPlayerState(org.bukkit.entity.Player player, me.marti.vchat.storage.PlayerState state) {
        privateMessageManager.applyState(player, state.msgEnabled(), state.socialSpy());
        adminManager.applyState(player, state.personalChatMuted(), state.deathMuted(), state.notifyEnabled());
        mentionManager.setMentionsEnabled(player, state.mentionsEnabled());
        ignoreManager.applyState(player, state.ignores());
    }

    private void runMain(Runnable task) {
        if (!isEnabled()) return;
        if (getServer().isPrimaryThread()) task.run();
        else getServer().getScheduler().runTask(this, task);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private Listener createChatListener() {
        try {
            Class<?> listenerClass = Class.forName("me.marti.vchat.listeners.ChatListener");
            return (Listener) listenerClass
                    .getConstructor(VChat.class,
                            me.marti.vchat.managers.FormatManager.class,
                            me.marti.vchat.processors.MessageProcessor.class,
                            me.marti.vchat.managers.FilterManager.class,
                            me.marti.vchat.managers.MentionManager.class)
                    .newInstance(this, formatManager, messageProcessor, filterManager, mentionManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ChatListener", e);
        }
    }

    private Listener createChatTabListener() {
        try {
            Class<?> listenerClass = Class.forName("me.marti.vchat.listeners.ChatTabListener");
            return (Listener) listenerClass.getConstructor(VChat.class).newInstance(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ChatTabListener", e);
        }
    }
}
