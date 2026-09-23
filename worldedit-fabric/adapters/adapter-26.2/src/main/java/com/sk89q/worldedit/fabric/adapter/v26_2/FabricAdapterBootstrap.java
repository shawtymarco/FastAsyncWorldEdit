package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.FAWEPlatformAdapterImpl;
import com.fastasyncworldedit.core.extent.processor.lighting.RelighterFactory;

/**
 * Entry point for the Fabric 26.2 FAWE performance adapter.
 */
public final class FabricAdapterBootstrap {

    private static FabricFaweAdapter adapter;

    private FabricAdapterBootstrap() {
    }

    public static void init() {
        if (adapter == null) {
            adapter = new FabricFaweAdapter();
        }
        com.sk89q.worldedit.coremc.internal.FaweChunkAccess.CHUNK_GET = (world, packed) -> {
            int x = (int) (packed >> 32);
            int z = (int) packed.longValue();
            return adapter.get(world, x, z);
        };
        com.sk89q.worldedit.coremc.internal.FaweChunkAccess.REGENERATE =
                com.sk89q.worldedit.fabric.adapter.v26_2.regen.FabricRegen::regenerate;
    }

    public static FabricFaweAdapter getAdapter() {
        if (adapter == null) {
            init();
        }
        return adapter;
    }

    public static FAWEPlatformAdapterImpl getPlatformAdapter() {
        return FabricChunkPlatformAdapter.INSTANCE;
    }

    public static RelighterFactory getRelighterFactory() {
        return FabricStarlightRelighterFactory.INSTANCE;
    }

    /**
     * Thin FAWEPlatformAdapterImpl that delegates to FabricPlatformAdapter helpers.
     */
    public static final class FabricChunkPlatformAdapter implements FAWEPlatformAdapterImpl {
        public static final FabricChunkPlatformAdapter INSTANCE = new FabricChunkPlatformAdapter();

        @Override
        public void sendChunk(com.fastasyncworldedit.core.queue.IChunkGet chunk, int mask, boolean lighting) {
            FabricNmsAdapter.sendChunkPacket(chunk);
        }
    }
}
