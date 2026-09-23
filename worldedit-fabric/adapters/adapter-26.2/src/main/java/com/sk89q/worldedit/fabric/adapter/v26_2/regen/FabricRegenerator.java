package com.sk89q.worldedit.fabric.adapter.v26_2.regen;

import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkCache;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.SingleThreadQueueExtent;
import com.fastasyncworldedit.core.util.TaskManager;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.coremc.internal.CoreMcWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.pattern.Pattern;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.block.BaseBlock;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;

/**
 * Fabric-side regeneration handler (Bukkit-free port of FAWE {@code Regenerator}).
 */
public abstract class FabricRegenerator {

    protected final CoreMcWorld originalWorld;
    protected final Region region;
    protected final Extent target;
    protected final RegenOptions options;

    protected ServerLevel originalServerLevel;
    protected long seed;
    protected SingleThreadQueueExtent source;

    protected FabricRegenerator(CoreMcWorld originalWorld, Region region, Extent target, RegenOptions options) {
        this.originalWorld = originalWorld;
        this.region = region;
        this.target = target;
        this.options = options;
    }

    public boolean regenerate() throws Exception {
        if (!prepare()) {
            return false;
        }

        try {
            if (!initNewWorld()) {
                cleanup0();
                return false;
            }
        } catch (Exception e) {
            cleanup0();
            throw e;
        }

        try {
            copyToWorld();
        } catch (Exception e) {
            cleanup0();
            throw e;
        }

        cleanup0();
        return true;
    }

    protected abstract void runTasks(BooleanSupplier shouldKeepTicking);

    protected abstract boolean prepare();

    protected abstract boolean initNewWorld() throws Exception;

    protected abstract void cleanup();

    protected abstract IChunkCache<IChunkGet> initSourceQueueCache();

    private void cleanup0() {
        cleanup();
    }

    private void createSource() {
        ServerLevel level = originalServerLevel;
        source = new SingleThreadQueueExtent(level.getMinY(), level.getMaxY());
        source.init(target, initSourceQueueCache(), null);
    }

    private void copyToWorld() {
        createSource();
        final long timeoutPerTick = TimeUnit.MILLISECONDS.toNanos(10);
        int taskId = TaskManager.taskManager().repeat(() -> {
            final long startTime = System.nanoTime();
            runTasks(() -> System.nanoTime() - startTime < timeoutPerTick);
        }, 1);

        boolean genBiomes = options.shouldRegenBiomes();
        boolean hasBiome = options.hasBiomeType();
        BiomeType biome = options.getBiomeType();
        Pattern pattern;
        if (!genBiomes && !hasBiome) {
            pattern = new PlacementPattern();
        } else if (hasBiome) {
            pattern = new WithBiomePlacementPattern((ignored1, ignored2) -> biome);
        } else {
            pattern = new WithBiomePlacementPattern((vec, chunk) -> {
                if (chunk != null) {
                    return chunk.getBiomeType(vec.x() & 15, vec.y(), vec.z() & 15);
                }
                return source.getBiome(vec);
            });
        }
        target.setBlocks(region, pattern);
        TaskManager.taskManager().cancel(taskId);
    }

    private abstract class ChunkwisePattern implements Pattern {
        protected @Nullable IChunk chunk;

        @Override
        public @NotNull <T extends IChunk> T applyChunk(final T chunk, @Nullable final Region region) {
            this.chunk = source.getOrCreateChunk(chunk.getX(), chunk.getZ());
            return chunk;
        }

        @Override
        public void finishChunk(final IChunk chunk) {
            this.chunk = null;
        }

        @Override
        public abstract Pattern fork();
    }

    private class PlacementPattern extends ChunkwisePattern {

        @Override
        public BaseBlock applyBlock(final BlockVector3 position) {
            return source.getFullBlock(position);
        }

        @Override
        public boolean apply(final Extent extent, final BlockVector3 get, final BlockVector3 set) throws WorldEditException {
            BaseBlock fullBlock;
            if (chunk != null) {
                fullBlock = chunk.getFullBlock(get.x() & 15, get.y(), get.z() & 15);
            } else {
                fullBlock = source.getFullBlock(get.x(), get.y(), get.z());
            }
            return set.setFullBlock(extent, fullBlock);
        }

        @Override
        public Pattern fork() {
            return new PlacementPattern();
        }
    }

    private class WithBiomePlacementPattern extends ChunkwisePattern {

        private final BiFunction<BlockVector3, @Nullable IChunk, BiomeType> biomeGetter;

        private WithBiomePlacementPattern(final BiFunction<BlockVector3, @Nullable IChunk, BiomeType> biomeGetter) {
            this.biomeGetter = biomeGetter;
        }

        @Override
        public BaseBlock applyBlock(final BlockVector3 position) {
            return source.getFullBlock(position);
        }

        @Override
        public boolean apply(final Extent extent, final BlockVector3 get, final BlockVector3 set) throws WorldEditException {
            final BaseBlock fullBlock;
            if (chunk != null) {
                fullBlock = chunk.getFullBlock(get.x() & 15, get.y(), get.z() & 15);
            } else {
                fullBlock = source.getFullBlock(get.x(), get.y(), get.z());
            }
            return extent.setBlock(set.x(), set.y(), set.z(), fullBlock)
                    && extent.setBiome(set.x(), set.y(), set.z(), biomeGetter.apply(get, chunk));
        }

        @Override
        public Pattern fork() {
            return new WithBiomePlacementPattern(this.biomeGetter);
        }
    }
}