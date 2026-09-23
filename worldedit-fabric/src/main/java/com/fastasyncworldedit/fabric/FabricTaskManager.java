package com.fastasyncworldedit.fabric;

import com.fastasyncworldedit.core.util.TaskManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fabric server-thread / async task scheduler for FAWE.
 */
public class FabricTaskManager extends TaskManager {

    private static volatile MinecraftServer server;

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<Integer, ScheduledTask> TASKS = new ConcurrentHashMap<>();
    private static final ExecutorService ASYNC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "FAWE-Fabric-Async");
        t.setDaemon(true);
        return t;
    });
    private static boolean ticksHooked;

    public FabricTaskManager() {
        hookTicks();
    }

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    public static MinecraftServer serverOrNull() {
        return server;
    }

    private static synchronized void hookTicks() {
        if (ticksHooked) {
            return;
        }
        ticksHooked = true;
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            server = s;
            Iterator<Map.Entry<Integer, ScheduledTask>> it = TASKS.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, ScheduledTask> entry = it.next();
                ScheduledTask task = entry.getValue();
                if (task.async) {
                    continue;
                }
                if (--task.delayLeft > 0) {
                    continue;
                }
                try {
                    task.runnable.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                if (task.period > 0) {
                    task.delayLeft = task.period;
                } else {
                    it.remove();
                }
            }
        });
    }

    @Override
    public int repeat(@Nonnull Runnable runnable, int interval) {
        int id = NEXT_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(runnable, interval, interval, false));
        return id;
    }

    @Override
    public int repeatAsync(@Nonnull Runnable runnable, int interval) {
        int id = NEXT_ID.getAndIncrement();
        ScheduledTask task = new ScheduledTask(runnable, interval, interval, true);
        TASKS.put(id, task);
        ASYNC.execute(() -> runAsyncLoop(id, task));
        return id;
    }

    private void runAsyncLoop(int id, ScheduledTask task) {
        while (TASKS.containsKey(id)) {
            try {
                Thread.sleep(Math.max(1, task.period) * 50L);
                if (!TASKS.containsKey(id)) {
                    break;
                }
                task.runnable.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public void async(@Nonnull Runnable runnable) {
        ASYNC.execute(runnable);
    }

    @Override
    public void task(@Nonnull Runnable runnable) {
        MinecraftServer s = server;
        if (s != null) {
            s.execute(runnable);
        } else {
            runnable.run();
        }
    }

    @Override
    public void later(@Nonnull Runnable runnable, int delay) {
        int id = NEXT_ID.getAndIncrement();
        TASKS.put(id, new ScheduledTask(runnable, Math.max(1, delay), 0, false));
    }

    @Override
    public void laterAsync(@Nonnull Runnable runnable, int delay) {
        ASYNC.execute(() -> {
            try {
                Thread.sleep(Math.max(1, delay) * 50L);
                runnable.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public void cancel(int task) {
        if (task != -1) {
            TASKS.remove(task);
        }
    }

    private static final class ScheduledTask {
        private final Runnable runnable;
        private final int period;
        private final boolean async;
        private int delayLeft;

        private ScheduledTask(Runnable runnable, int delayLeft, int period, boolean async) {
            this.runnable = runnable;
            this.delayLeft = delayLeft;
            this.period = period;
            this.async = async;
        }
    }
}
