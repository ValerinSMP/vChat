package me.marti.vchat.redis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LifecycleGateTest {
    @Test
    void reloadCannotStartDuplicateSubscriber() {
        LifecycleGate gate = new LifecycleGate();
        assertTrue(gate.start());
        assertFalse(gate.start());
        gate.stop();
        assertTrue(gate.start());
    }
}
