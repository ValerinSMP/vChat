package me.marti.vchat.redis;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RedisEnvelopeTest {
    private final Gson gson = new Gson();

    @Test
    void acceptsValidEnvelopeAndRoundTripsPayload() {
        long now = System.currentTimeMillis();
        RedisEvent event = new RedisEvent(RedisEventType.PRIVATE_MSG).put("targetUuid", "target");
        event.networkId = "valerin";
        event.sourceServer = "survival-1";
        event.createdAt = now;
        String json = gson.toJson(event);
        RedisEvent parsed = gson.fromJson(json, RedisEvent.class);
        assertTrue(RedisEnvelopeValidator.valid(parsed, "valerin", now, json.getBytes(StandardCharsets.UTF_8).length));
        assertEquals("target", parsed.payload.get("targetUuid"));
    }

    @Test
    void rejectsWrongVersionUuidNetworkTypeAgeAndOversize() {
        long now = System.currentTimeMillis();
        RedisEvent event = valid(now);
        event.schemaVersion = 2;
        assertFalse(RedisEnvelopeValidator.valid(event, "valerin", now, 100));
        event = valid(now); event.eventId = "bad";
        assertFalse(RedisEnvelopeValidator.valid(event, "valerin", now, 100));
        event = valid(now); event.networkId = "other";
        assertFalse(RedisEnvelopeValidator.valid(event, "valerin", now, 100));
        event = valid(now); event.type = null;
        assertFalse(RedisEnvelopeValidator.valid(event, "valerin", now, 100));
        event = valid(now - 300_001L);
        assertFalse(RedisEnvelopeValidator.valid(event, "valerin", now, 100));
        assertFalse(RedisEnvelopeValidator.valid(valid(now), "valerin", now, RedisEnvelopeValidator.MAX_EVENT_BYTES + 1));
    }

    @Test
    void dropsLoopbackAndBoundedDuplicates() {
        RedisEvent event = valid(System.currentTimeMillis());
        assertTrue(RedisEnvelopeValidator.loopback(event, "survival-1"));
        assertFalse(RedisEnvelopeValidator.loopback(event, "survival-2"));
        EventDeduplicator dedupe = new EventDeduplicator(2, 1000L);
        assertTrue(dedupe.first("a", 1L));
        assertFalse(dedupe.first("a", 2L));
        assertTrue(dedupe.first("b", 3L));
        assertTrue(dedupe.first("c", 4L));
        assertEquals(2, dedupe.size());
    }

    private static RedisEvent valid(long createdAt) {
        RedisEvent event = new RedisEvent(RedisEventType.CHAT);
        event.networkId = "valerin";
        event.sourceServer = "survival-1";
        event.createdAt = createdAt;
        return event;
    }
}
