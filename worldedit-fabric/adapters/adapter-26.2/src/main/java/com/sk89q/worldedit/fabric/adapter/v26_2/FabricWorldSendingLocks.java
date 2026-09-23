package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.math.IntPair;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-world chunk send/write locks (Fabric replacement for FaweBukkitWorld maps).
 */
public final class FabricWorldSendingLocks {

    private static final ConcurrentHashMap<String, ConcurrentHashMap<IntPair, FabricNmsAdapter.ChunkSendLock>> WORLD_MAPS =
            new ConcurrentHashMap<>();

    private FabricWorldSendingLocks() {
    }

    public static ConcurrentHashMap<IntPair, FabricNmsAdapter.ChunkSendLock> getWorldSendingChunksMap(String worldName) {
        return WORLD_MAPS.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
    }
}
