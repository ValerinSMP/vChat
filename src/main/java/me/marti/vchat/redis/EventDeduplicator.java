package me.marti.vchat.redis;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class EventDeduplicator {
    private final int maximumSize;
    private final long ttlMillis;
    private final LinkedHashMap<String, Long> seen = new LinkedHashMap<>();

    EventDeduplicator(int maximumSize, long ttlMillis) {
        this.maximumSize = maximumSize;
        this.ttlMillis = ttlMillis;
    }

    synchronized boolean first(String eventId, long now) {
        Iterator<Map.Entry<String, Long>> iterator = seen.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue() > ttlMillis) iterator.remove();
            else break;
        }
        if (seen.containsKey(eventId)) return false;
        seen.put(eventId, now);
        while (seen.size() > maximumSize) seen.remove(seen.keySet().iterator().next());
        return true;
    }

    synchronized int size() {
        return seen.size();
    }
}
