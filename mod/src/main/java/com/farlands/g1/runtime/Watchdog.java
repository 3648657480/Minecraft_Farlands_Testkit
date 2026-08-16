package com.farlands.g1.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hang detector: every 10 seconds samples all busy threads. A thread whose
 * stack top has not changed since the previous sample is considered stuck
 * (infinite loop / recursion) and its full stack is printed once.
 */
public final class Watchdog {

    private static final Map<String, String> LAST_TOP = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_REPORT = new ConcurrentHashMap<>();

    private Watchdog() {
    }

    public static void start() {
        Thread t = new Thread(() -> {
            Runtime rt = Runtime.getRuntime();
            while (true) {
                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException e) {
                    return;
                }
                long used = rt.totalMemory() - rt.freeMemory();
                System.out.println("[Watchdog] heap used=" + (used >> 20) + "MB max=" + (rt.maxMemory() >> 20) + "MB");
                long now = System.currentTimeMillis();
                for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
                    Thread th = e.getKey();
                    String name = th.getName();
                    boolean interesting = name.contains("Worker") || name.equals("Render thread")
                        || name.equals("Server thread");
                    if (!interesting) {
                        continue;
                    }
                    StackTraceElement[] st = e.getValue();
                    if (st.length == 0) {
                        continue;
                    }
                    String top = st[0].getClassName() + "." + st[0].getMethodName() + ":" + st[0].getLineNumber();
                    String prev = LAST_TOP.put(name, top);
                    if (prev != null && prev.equals(top) && top.startsWith("net.minecraft.")) {
                        Long last = LAST_REPORT.get(name);
                        if (last != null && now - last < 30_000L) {
                            continue; // already reported recently
                        }
                        LAST_REPORT.put(name, now);
                        System.out.println("[Watchdog] STUCK thread=" + name + " state=" + th.getState());
                        int n = Math.min(st.length, 24);
                        for (int i = 0; i < n; i++) {
                            System.out.println("    at " + st[i]);
                        }
                    }
                }
            }
        }, "FarLands-Watchdog");
        t.setDaemon(true);
        t.start();
    }
}
