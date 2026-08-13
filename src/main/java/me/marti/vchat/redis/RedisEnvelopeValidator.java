package me.marti.vchat.redis;

import java.util.UUID;
import java.util.regex.Pattern;

public final class RedisEnvelopeValidator {
    public static final int MAX_EVENT_BYTES = 65_536;
    private static final long MAX_AGE_MILLIS = 300_000L;
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private RedisEnvelopeValidator() {
    }

    public static boolean valid(RedisEvent event, String networkId, long now, int rawBytes) {
        if (event == null || rawBytes <= 0 || rawBytes > MAX_EVENT_BYTES || event.schemaVersion != 1
                || event.type == null || event.payload == null || !ID.matcher(nullToEmpty(event.networkId)).matches()
                || !ID.matcher(nullToEmpty(event.sourceServer)).matches() || !networkId.equals(event.networkId)) {
            return false;
        }
        try {
            UUID.fromString(event.eventId);
        } catch (RuntimeException invalid) {
            return false;
        }
        return event.createdAt > 0 && event.createdAt <= now + 30_000L && now - event.createdAt <= MAX_AGE_MILLIS;
    }

    public static boolean loopback(RedisEvent event, String serverId) {
        return event != null && serverId.equals(event.sourceServer);
    }

    public static boolean validId(String value) {
        return ID.matcher(nullToEmpty(value)).matches();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
