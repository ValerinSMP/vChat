package me.marti.vchat.storage;

import java.util.Set;
import java.util.UUID;

public record PlayerState(boolean msgEnabled, boolean socialSpy, boolean personalChatMuted,
        boolean mentionsEnabled, boolean deathMuted, boolean notifyEnabled, Set<UUID> ignores) {
    public PlayerState {
        ignores = Set.copyOf(ignores);
    }
}
