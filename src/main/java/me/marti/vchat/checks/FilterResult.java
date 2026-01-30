package me.marti.vchat.checks;

import net.kyori.adventure.text.Component;

public record FilterResult(State state, String reason, String modifiedMessage, Component reasonMessage) {

    public enum State {
        ALLOWED,
        BLOCKED,
        MODIFIED
    }

    public static FilterResult allowed() {
        return new FilterResult(State.ALLOWED, null, null, null);
    }

    public static FilterResult blocked(String reason, Component reasonMessage) {
        return new FilterResult(State.BLOCKED, reason, null, reasonMessage);
    }

    public static FilterResult modified(String reason, String modifiedMessage) {
        return new FilterResult(State.MODIFIED, reason, modifiedMessage, null);
    }
}
