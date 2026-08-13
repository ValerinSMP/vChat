package me.marti.vchat.storage;

import me.marti.vchat.VChat;
import me.marti.vchat.managers.ConfigManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageManagerTest {
    @TempDir Path tempDir;

    @Test
    void pdcMigrationIsIdempotentAndNeverOverwritesNewDurableState() {
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            StorageManager storage = storage(io);
            UUID player = UUID.randomUUID();
            UUID ignored = UUID.randomUUID();
            PlayerState legacy = new PlayerState(false, true, true, false, true, true, Set.of(ignored));
            assertEquals(legacy, storage.loadOrMigrate(player, "Martin", legacy).join());

            PlayerState stalePdc = StorageManager.defaults();
            assertEquals(legacy, storage.loadOrMigrate(player, "Martin", stalePdc).join());

            PlayerState newer = new PlayerState(true, false, false, true, false, false, Set.of());
            storage.save(player, "Martin", newer).join();
            assertEquals(newer, storage.loadOrMigrate(player, "Martin", legacy).join());
        } finally {
            io.shutdownNow();
        }
    }

    @Test
    void sharedTogglesIgnoresMuteAndFirstJoinAreDurable() {
        ExecutorService io = Executors.newSingleThreadExecutor();
        try {
            StorageManager storage = storage(io);
            UUID player = UUID.randomUUID();
            UUID ignored = UUID.randomUUID();
            PlayerState state = new PlayerState(false, true, true, false, true, true, Set.of(ignored));
            storage.save(player, "ExactName", state).join();
            assertEquals(state, storage.load(player).join());
            storage.saveGlobalMute(true).join();
            assertTrue(storage.loadGlobalMute().join());
            PlayerRegistration first = storage.registerPlayer(player, "ExactName").join();
            PlayerRegistration repeated = storage.registerPlayer(player, "ExactName").join();
            assertTrue(first.firstNetworkJoin());
            assertFalse(repeated.firstNetworkJoin());
            assertEquals(first.playerNumber(), repeated.playerNumber());
        } finally {
            io.shutdownNow();
        }
    }

    private StorageManager storage(ExecutorService io) {
        VChat plugin = mock(VChat.class);
        ConfigManager configs = mock(ConfigManager.class);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("storage.type", "sqlite");
        yaml.set("storage.sqlite-file", "test.db");
        when(plugin.getConfigManager()).thenReturn(configs);
        when(configs.getMainConfig()).thenReturn(yaml);
        when(plugin.getDataFolder()).thenReturn(new File(tempDir.toFile(), UUID.randomUUID().toString()));
        StorageManager storage = new StorageManager(plugin, io);
        storage.ready().join();
        return storage;
    }
}
