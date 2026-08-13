package me.marti.vchat.utils;

import java.util.function.Consumer;

public final class MainThreadGate {
    private MainThreadGate() { }

    public static void dispatch(boolean primaryThread, Runnable task, Consumer<Runnable> scheduler) {
        if (primaryThread) task.run();
        else scheduler.accept(task);
    }
}
