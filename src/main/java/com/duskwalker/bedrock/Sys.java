package com.duskwalker.bedrock;

import java.util.ArrayList;
import java.util.List;

/**
 * Bedrock "system" nesnesinin karsiligi (tick zamanlayici). Saf Java; binding her sunucu tikinde Sys.tick() cagirir.
 * Donen int degerler clearRun'a verilir. Gorev hatalari yutulur.
 */
public final class Sys {
    private Sys() {}

    private static final class Task {
        final int id; final Runnable fn; long nextTick; final int interval; boolean cancelled;
        Task(int id, Runnable fn, long nextTick, int interval) { this.id = id; this.fn = fn; this.nextTick = nextTick; this.interval = interval; }
    }

    private static final List<Task> tasks = new ArrayList<>();
    private static final List<Task> pending = new ArrayList<>();
    private static long currentTick = 0;
    private static int nextId = 1;
    private static boolean running = false;

    public static long currentTick() { return currentTick; }

    /** Bir sonraki tikte calisir (system.run). */
    public static int run(Runnable fn) { return schedule(fn, 1, 0); }
    /** delayTicks sonra bir kez (system.runTimeout). */
    public static int runTimeout(Runnable fn, int delayTicks) { return schedule(fn, Math.max(1, delayTicks), 0); }
    /** Her intervalTicks'te bir (system.runInterval); ilk calisma intervalTicks sonra. */
    public static int runInterval(Runnable fn, int intervalTicks) {
        int iv = Math.max(1, intervalTicks);
        return schedule(fn, iv, iv);
    }

    public static void clearRun(int id) {
        for (Task t : tasks) if (t.id == id) t.cancelled = true;
        for (Task t : pending) if (t.id == id) t.cancelled = true;
    }

    private static int schedule(Runnable fn, int delay, int interval) {
        Task t = new Task(nextId++, fn, currentTick + delay, interval);
        if (running) pending.add(t); else tasks.add(t);
        return t.id;
    }

    /** Binding: her sunucu tikinin sonunda cagir. */
    public static void tick() {
        currentTick++;
        running = true;
        try {
            for (int i = 0; i < tasks.size(); i++) {
                Task t = tasks.get(i);
                if (t.cancelled || t.nextTick > currentTick) continue;
                try { t.fn.run(); } catch (Throwable ex) { Log.warn("scheduled task failed: " + ex); }
                if (t.interval > 0 && !t.cancelled) t.nextTick = currentTick + t.interval; else t.cancelled = true;
            }
        } finally {
            running = false;
        }
        tasks.removeIf(t -> t.cancelled);
        if (!pending.isEmpty()) { tasks.addAll(pending); pending.clear(); }
    }

    /** Sunucu kapanirken / yeniden yuklenirken. */
    public static void reset() { tasks.clear(); pending.clear(); currentTick = 0; nextId = 1; }
}
