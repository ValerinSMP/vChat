package me.marti.vchat.redis;

import java.util.concurrent.atomic.AtomicBoolean;

final class LifecycleGate {
    private final AtomicBoolean active = new AtomicBoolean();
    boolean start() { return active.compareAndSet(false, true); }
    void stop() { active.set(false); }
    boolean active() { return active.get(); }
}
