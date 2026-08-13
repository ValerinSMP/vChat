package me.marti.vchat.utils;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MainThreadGateTest {
    @Test
    void redisCallbackSchedulesInsteadOfTouchingBukkitOffThread() {
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        MainThreadGate.dispatch(false, () -> ran.set(true), scheduled::set);
        assertFalse(ran.get());
        assertNotNull(scheduled.get());
        scheduled.get().run();
        assertTrue(ran.get());
    }
}
