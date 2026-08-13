package me.marti.vchat.redis;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Versioned event envelope shared by every vChat backend. */
public final class RedisEvent {
    public int schemaVersion = 1;
    public String eventId = UUID.randomUUID().toString();
    public String networkId;
    public String sourceServer;
    public RedisEventType type;
    public long createdAt;
    public Map<String, String> payload = new LinkedHashMap<>();

    public RedisEvent() {
    }

    public RedisEvent(RedisEventType type) {
        this.type = type;
    }

    public RedisEvent put(String key, String value) {
        if (value != null) payload.put(key, value);
        return this;
    }
}
