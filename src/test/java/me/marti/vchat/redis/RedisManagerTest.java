package me.marti.vchat.redis;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisManagerTest {

    @Test
    void keysAndChannelAreNamespacedByCluster() {
        assertEquals("vchat:valerin:events", RedisManager.channelName("valerin"));
        assertEquals("vchat:valerin:presence", RedisManager.presenceKey("valerin"));
        assertEquals("vchat:valerin:msgtoggle", RedisManager.msgToggleKey("valerin"));

        UUID uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertEquals("vchat:valerin:ignore:11111111-1111-1111-1111-111111111111",
                RedisManager.ignoreKey("valerin", uuid));
    }

    @Test
    void presenceValueRoundTripsThroughParsing() {
        UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String value = RedisManager.presenceValue("survival1", uuid);

        String[] parsed = RedisManager.parsePresenceValue(value);

        assertArrayEquals(new String[]{"survival1", uuid.toString()}, parsed);
    }

    @Test
    void parsePresenceValueRejectsMissingOrCorruptData() {
        assertNull(RedisManager.parsePresenceValue(null));
        assertNull(RedisManager.parsePresenceValue("no-separator"));
    }

    @Test
    void loopbackEventsFromTheSameServerAreDropped() {
        RedisEvent local = new RedisEvent();
        local.type = RedisEventType.CHAT;
        local.sourceServer = "survival1";
        assertTrue(RedisManager.isLoopback(local, "survival1"));

        RedisEvent remote = new RedisEvent();
        remote.type = RedisEventType.CHAT;
        remote.sourceServer = "survival2";
        assertFalse(RedisManager.isLoopback(remote, "survival1"));

        assertTrue(RedisManager.isLoopback(null, "survival1"));

        RedisEvent noSource = new RedisEvent();
        assertTrue(RedisManager.isLoopback(noSource, "survival1"));
    }

    @Test
    void eventsWithNullTypeAreTreatedAsLoopbackAndDropped() {
        // Gson no lanza al deserializar un enum desconocido, deja 'type' en null en vez de
        // tirar JsonSyntaxException (ver RedisEventTest) — isLoopback es la única red de
        // seguridad antes de RedisEventHandler.handle(), que haría NPE en su switch si esto
        // no se filtrara acá.
        RedisEvent corrupted = new RedisEvent();
        corrupted.sourceServer = "survival2";
        corrupted.type = null;

        assertTrue(RedisManager.isLoopback(corrupted, "survival1"));
    }
}
