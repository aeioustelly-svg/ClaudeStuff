package com.aceliada.summon;

import com.aceliada.Aceliada;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs short delayed tasks on the server thread. Pending tasks are dropped when the server stops. */
@Mod.EventBusSubscriber(modid = Aceliada.MODID)
public final class ServerScheduler {
    private static final List<Task> TASKS = new ArrayList<>();

    private record Task(Runnable action, int[] remaining) {
    }

    private ServerScheduler() {
    }

    public static void schedule(int delayTicks, Runnable action) {
        TASKS.add(new Task(action, new int[]{delayTicks}));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty()) {
            return;
        }
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (--task.remaining()[0] <= 0) {
                due.add(task);
                it.remove();
            }
        }
        // Run after the sweep, so a task may schedule further tasks.
        due.forEach(task -> task.action().run());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TASKS.clear();
    }
}
