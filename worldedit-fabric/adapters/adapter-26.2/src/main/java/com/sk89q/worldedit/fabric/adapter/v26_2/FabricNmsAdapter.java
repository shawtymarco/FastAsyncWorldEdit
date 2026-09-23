package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.FAWEPlatformAdapterImpl;
import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.math.IntPair;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.util.MathMan;
import com.fastasyncworldedit.core.util.ReflectionUtils;
import com.sk89q.worldedit.world.block.BlockTypesCache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntFunction;

public class FabricNmsAdapter implements FAWEPlatformAdapterImpl {

    public static int createPalette(
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            char[] set,
            FabricFaweAdapter adapter,
            final boolean globalKindaDoesNotExist
    ) {
        int numPaletteEntries = 0;
        for (int i = 0; i < 4096; i++) {
            int ordinal = set[i];
            ordinal = Math.max(ordinal, BlockTypesCache.ReservedIDs.AIR);
            int palette = blockToPalette[ordinal];
            if (palette == Integer.MAX_VALUE) {
                blockToPalette[ordinal] = numPaletteEntries;
                paletteToBlock[numPaletteEntries] = ordinal;
                numPaletteEntries++;
            }
        }
        mapPalette(blockToPalette, paletteToBlock, blocksCopy, set, adapter, numPaletteEntries, globalKindaDoesNotExist);
        return numPaletteEntries;
    }

    public static int createPalette(
            int layer,
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            IntFunction<char[]> get,
            char[] set,
            FabricFaweAdapter adapter,
            final boolean globalKindaDoesNotExist
    ) {
        int numPaletteEntries = 0;
        char[] getArr = null;
        for (int i = 0; i < 4096; i++) {
            char ordinal = set[i];
            if (ordinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                if (getArr == null) {
                    getArr = get.apply(layer);
                }
                ordinal = getArr[i];
                set[i] = (char) Math.max(ordinal, BlockTypesCache.ReservedIDs.AIR);
            }
            int palette = blockToPalette[ordinal];
            if (palette == Integer.MAX_VALUE) {
                blockToPalette[ordinal] = numPaletteEntries;
                paletteToBlock[numPaletteEntries] = ordinal;
                numPaletteEntries++;
            }
        }
        mapPalette(blockToPalette, paletteToBlock, blocksCopy, set, adapter, numPaletteEntries, globalKindaDoesNotExist);
        return numPaletteEntries;
    }

    private static void mapPalette(
            int[] blockToPalette,
            int[] paletteToBlock,
            int[] blocksCopy,
            char[] set,
            FabricFaweAdapter adapter,
            int numPaletteEntries,
            boolean globalKindaDoesNotExist
    ) {
        int bitsPerEntry = MathMan.log2nlz(numPaletteEntries - 1);
        if (!globalKindaDoesNotExist && bitsPerEntry > 8 && adapter != null) {
            System.arraycopy(adapter.getIbdToOrdinal(), 0, paletteToBlock, 0, adapter.getIbdToOrdinal().length);
            System.arraycopy(adapter.getOrdinalToIbdID(), 0, blockToPalette, 0, adapter.getOrdinalToIbdID().length);
        }
        int oldVal = blockToPalette[BlockTypesCache.ReservedIDs.__RESERVED__];
        blockToPalette[BlockTypesCache.ReservedIDs.__RESERVED__] = blockToPalette[BlockTypesCache.ReservedIDs.AIR];
        for (int i = 0; i < 4096; i++) {
            int ordinal = set[i];
            int palette = blockToPalette[ordinal];
            blocksCopy[i] = palette;
        }
        blockToPalette[BlockTypesCache.ReservedIDs.__RESERVED__] = oldVal;
    }

    @Override
    public void sendChunk(IChunkGet chunk, int mask, boolean lighting) {
        sendChunkPacket(chunk);
    }

    public static void sendChunkPacket(IChunkGet chunk) {
        if (!(chunk instanceof AbstractFabricGetBlocks<?, ?> getBlocks)) {
            throw new IllegalArgumentException("(IChunkGet) chunk not of type AbstractFabricGetBlocks");
        }
        getBlocks.send();
    }

    protected static <LevelChunkSection> boolean setSectionAtomic(
            String worldName,
            IntPair pair,
            LevelChunkSection[] sections,
            LevelChunkSection expected,
            LevelChunkSection value,
            int layer
    ) {
        if (layer < 0 || layer >= sections.length) {
            return false;
        }
        if (Fawe.isMainThread()) {
            return ReflectionUtils.compareAndSet(sections, expected, value, layer);
        }
        StampLockHolder holder = new StampLockHolder();
        ConcurrentHashMap<IntPair, ChunkSendLock> chunks = FabricWorldSendingLocks.getWorldSendingChunksMap(worldName);
        chunks.compute(pair, (k, lock) -> {
            if (lock == null) {
                lock = new ChunkSendLock();
            } else if (lock.writeWaiting) {
                throw new IllegalStateException("Attempting to write chunk section when write is already ongoing?!");
            }
            holder.stamp = lock.lock.tryWriteLock();
            holder.chunkLock = lock;
            lock.writeWaiting = true;
            return lock;
        });
        try {
            if (holder.stamp == 0) {
                holder.stamp = holder.chunkLock.lock.writeLock();
            }
            return ReflectionUtils.compareAndSet(sections, expected, value, layer);
        } finally {
            chunks = FabricWorldSendingLocks.getWorldSendingChunksMap(worldName);
            chunks.computeIfPresent(pair, (k, lock) -> {
                if (lock != holder.chunkLock) {
                    throw new IllegalStateException("SENDING_CHUNKS stored lock does not equal lock attempted to be unlocked?!");
                }
                lock.lock.unlockWrite(holder.stamp);
                lock.writeWaiting = false;
                return lock;
            });
        }
    }

    protected static void beginChunkPacketSend(String worldName, IntPair pair, StampLockHolder stampedLock) {
        ConcurrentHashMap<IntPair, ChunkSendLock> chunks = FabricWorldSendingLocks.getWorldSendingChunksMap(worldName);
        chunks.compute(pair, (k, lock) -> {
            if (lock == null) {
                lock = new ChunkSendLock();
            }
            if (lock.writeWaiting || lock.lock.getReadLockCount() >= 1 || lock.lock.isWriteLocked()) {
                return lock;
            }
            stampedLock.stamp = lock.lock.readLock();
            stampedLock.chunkLock = lock;
            return lock;
        });
    }

    protected static void endChunkPacketSend(String worldName, IntPair pair, StampLockHolder lockHolder) {
        ConcurrentHashMap<IntPair, ChunkSendLock> chunks = FabricWorldSendingLocks.getWorldSendingChunksMap(worldName);
        chunks.computeIfPresent(pair, (k, lock) -> {
            if (lock.lock != lockHolder.chunkLock.lock) {
                throw new IllegalStateException("SENDING_CHUNKS stored lock does not equal lock attempted to be unlocked?!");
            }
            lock.lock.unlockRead(lockHolder.stamp);
            return null;
        });
    }

    public static final class StampLockHolder {
        public long stamp;
        public ChunkSendLock chunkLock = null;
    }

    public static final class ChunkSendLock {
        public final StampedLock lock = new StampedLock();
        public boolean writeWaiting = false;
    }
}
