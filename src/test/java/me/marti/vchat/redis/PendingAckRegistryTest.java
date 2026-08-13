package me.marti.vchat.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PendingAckRegistryTest {
    @Test
    void successOfflineAndTimeoutCompleteAtMostOnce() {
        PendingAckRegistry<String> registry = new PendingAckRegistry<>();
        registry.add("delivered", "message-a");
        registry.add("offline", "message-b");
        assertEquals("message-a", registry.complete("delivered"));
        assertNull(registry.complete("delivered"));
        assertEquals("message-b", registry.complete("offline"));
        assertEquals(0, registry.size());
        assertTrue(DeliveryStatus.DELIVERED.delivered());
        assertFalse(DeliveryStatus.OFFLINE.delivered());
        assertFalse(DeliveryStatus.parse("unknown").delivered());
    }
}
