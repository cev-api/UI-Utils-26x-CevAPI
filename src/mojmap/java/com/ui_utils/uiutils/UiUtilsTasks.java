package com.ui_utils.uiutils;

import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Pending actions are dispatched by the client tick and cannot cross sessions. */
public final class UiUtilsTasks {
    private record Task(long due, ClientPacketListener session, long generation, Thread macro, Runnable action) {}
    private static final ConcurrentLinkedQueue<Task> TASKS = new ConcurrentLinkedQueue<>();
    private static volatile long generation;
    private static final ThreadLocal<Thread> MACRO = new ThreadLocal<>();
    public static void runOwned(Thread owner, Runnable action) {
        Thread previous = MACRO.get(); MACRO.set(owner);
        try { action.run(); } finally { if (previous == null) MACRO.remove(); else MACRO.set(previous); }
    }
    public static void cancelMacroTasks() { TASKS.removeIf(task -> task.macro != null); }
    private UiUtilsTasks() {}
    public static void schedule(Runnable action, long delayMs) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener session = mc.getConnection();
        long owner = generation;
        Thread macro = MACRO.get();
        mc.execute(() -> {
            if (owner != generation || session != mc.getConnection()) return;
            if (delayMs <= 0) action.run();
            else TASKS.add(new Task(System.nanoTime() + delayMs * 1_000_000L, session, owner, macro, action));
        });
    }
    public static void clear() { generation++; TASKS.clear(); }
    public static void tick(Minecraft mc) {
        long now = System.nanoTime();
        for (Task task : TASKS) {
            if (task.generation != generation || task.session != mc.getConnection()) TASKS.remove(task);
            else if (now >= task.due && TASKS.remove(task)) task.action.run();
        }
    }
}
