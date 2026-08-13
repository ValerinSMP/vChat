package me.marti.vchat.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerPrefixTest {

    @Test
    void migratesOnlyTheHistoricalDefaultPrefix() {
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("prefix", "<dark_gray>[</dark_gray><#00FB9A>vChat</#00FB9A><dark_gray>]</dark_gray> <reset>");
        YamlConfiguration current = new YamlConfiguration();
        current.set("prefix", "<gray>[<#00FB9A>vChat</#00FB9A><gray>]</gray> ");

        assertTrue(ConfigManager.migrateLegacyMessagePrefix(current, defaults));
        assertEquals(defaults.getString("prefix"), current.getString("prefix"));

        current.set("prefix", "<blue>[Mi chat]</blue> ");
        assertFalse(ConfigManager.migrateLegacyMessagePrefix(current, defaults));
        assertEquals("<blue>[Mi chat]</blue> ", current.getString("prefix"));
    }
}
