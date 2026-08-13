package me.marti.vchat.redis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PresenceTest {
    @Test
    void presenceRoundTripsAndUsesNamespacedTtlKeys() {
        UUID uuid = UUID.randomUUID();
        Presence presence = new Presence("survival-1", "session-a", uuid, "Martin");
        assertEquals(presence, Presence.parse(presence.encode()));
        assertNull(Presence.parse("broken"));
        assertEquals("valerin:red:vchat:", RedisManager.namespace("red"));
        assertEquals(15, RedisManager.validatedTtlSeconds(1));
        assertEquals(30, RedisManager.validatedTtlSeconds(30));
        // La transferencia real observada tardó 10 s: la ventana ya no puede volver a 5 s.
        assertEquals(400L, RedisManager.transferGraceTicks(20));
        assertEquals(30, RedisManager.quitPresenceTtlSeconds(20));
    }

    @Test
    void delayedQuitCannotClearNewSession() {
        UUID uuid = UUID.randomUUID();
        Presence old = new Presence("hub", "old", uuid, "Martin");
        Presence same = new Presence("hub", "old", uuid, "Martin");
        Presence moved = new Presence("survival", "new", uuid, "Martin");
        assertTrue(RedisManager.delayedQuitMatches(same, old));
        assertFalse(RedisManager.delayedQuitMatches(moved, old));
    }
}
