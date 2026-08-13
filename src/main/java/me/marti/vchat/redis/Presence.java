package me.marti.vchat.redis;

import java.util.UUID;

public record Presence(String serverId, String sessionId, UUID playerId, String playerName) {
    String encode() {
        return serverId + "|" + sessionId + "|" + playerId + "|" + playerName;
    }

    static Presence parse(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split("\\|", 4);
        if (parts.length != 4) return null;
        try {
            return new Presence(parts[0], parts[1], UUID.fromString(parts[2]), parts[3]);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
