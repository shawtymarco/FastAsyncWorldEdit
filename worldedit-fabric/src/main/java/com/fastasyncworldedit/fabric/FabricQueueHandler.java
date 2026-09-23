package com.fastasyncworldedit.fabric;

import com.fastasyncworldedit.core.queue.implementation.QueueHandler;

/**
 * Fabric queue handler. No Spigot AsyncCatcher / Timings to disable.
 */
public class FabricQueueHandler extends QueueHandler {

    @Override
    public void startUnsafe(boolean parallel) {
        // Fabric has no AsyncCatcher equivalent.
    }

    @Override
    public void endUnsafe(boolean parallel) {
        // no-op
    }
}
