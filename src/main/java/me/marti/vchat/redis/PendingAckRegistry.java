package me.marti.vchat.redis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingAckRegistry<T> {
    private final Map<String, T> pending = new ConcurrentHashMap<>();
    public void add(String eventId, T value) { pending.put(eventId, value); }
    public T complete(String eventId) { return pending.remove(eventId); }
    public boolean contains(String eventId) { return pending.containsKey(eventId); }
    public int size() { return pending.size(); }
}
