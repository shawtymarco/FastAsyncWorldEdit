package com.sk89q.worldedit.coremc.internal;

import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.packet.ChunkPacket;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.World;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Hooks registered by the Fabric FAWE adapter to avoid module cycles.
 */
public final class FaweChunkAccess {

    public static volatile BiFunction<World, Long, IChunkGet> CHUNK_GET;
    public static volatile FakeChunkSender FAKE_CHUNK_SENDER;
    public static volatile RegenHandler REGENERATE;

    private FaweChunkAccess() {
    }

    @FunctionalInterface
    public interface FakeChunkSender {
        void send(@Nullable Player player, ChunkPacket packet);
    }

    /**
     * Optional FAWE adapter hook for region regeneration (Fabric).
     */
    @FunctionalInterface
    public interface RegenHandler {
        boolean regenerate(CoreMcWorld world, Region region, Extent extent, RegenOptions options) throws Exception;
    }

    public static long pack(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }
}
