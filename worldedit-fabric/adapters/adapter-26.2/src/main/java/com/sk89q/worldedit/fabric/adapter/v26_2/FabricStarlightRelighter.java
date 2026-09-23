package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.extent.processor.lighting.Relighter;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Relighter that asks the vanilla light engine to recheck edited chunks.
 */
public class FabricStarlightRelighter implements Relighter {

    private final ServerLevel level;
    private final Set<ChunkPos> chunks = new LinkedHashSet<>();
    private final ReentrantLock lock = new ReentrantLock();
    private boolean finished;

    public FabricStarlightRelighter(ServerLevel level, IQueueExtent<?> queue) {
        this.level = level;
    }

    @Override
    public boolean addChunk(int cx, int cz, byte[] skipReason, int bitmask) {
        lock.lock();
        try {
            chunks.add(new ChunkPos(cx, cz));
            finished = false;
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void addLightUpdate(int x, int y, int z) {
        lock.lock();
        try {
            chunks.add(new ChunkPos(x >> 4, z >> 4));
            finished = false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            chunks.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void removeLighting() {
        // Vanilla light engine will recompute on fix* calls.
    }

    @Override
    public void fixBlockLighting() {
        fixLightingSafe(false);
    }

    @Override
    public void fixSkyLighting() {
        fixLightingSafe(true);
    }

    @Override
    public void fixLightingSafe(boolean sky) {
        lock.lock();
        try {
            for (ChunkPos pos : chunks) {
                level.getChunkSource().getLightEngine().checkBlock(
                        new BlockPos(pos.getMinBlockX(), level.getMinY(), pos.getMinBlockZ())
                );
                level.getChunkSource().getLightEngine().propagateLightSources(pos);
            }
            chunks.clear();
            finished = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        lock.lock();
        try {
            return chunks.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public ReentrantLock getLock() {
        return lock;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public void close() {
        fixLightingSafe(true);
    }
}
