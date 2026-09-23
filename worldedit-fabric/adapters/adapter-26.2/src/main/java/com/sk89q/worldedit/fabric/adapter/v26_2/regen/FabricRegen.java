package com.sk89q.worldedit.fabric.adapter.v26_2.regen;

import com.fastasyncworldedit.core.Fawe;
import com.fastasyncworldedit.core.queue.IChunkCache;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.implementation.chunk.ChunkCache;
import com.google.common.collect.ImmutableList;
import com.sk89q.worldedit.coremc.internal.CoreMcWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.fabric.adapter.v26_2.FabricAdapterBootstrap;
import com.sk89q.worldedit.fabric.adapter.v26_2.FabricFaweAdapter;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.io.file.SafeFiles;
import com.sk89q.worldedit.world.RegenOptions;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Regenerates a region into an {@link Extent} using a temporary {@link ServerLevel}.
 */
public final class FabricRegen extends FabricRegenerator {

    private static final String REGEN_WORLD_NAME = "faweregentempworld";

    private ServerLevel freshWorld;
    private LevelStorageSource.LevelStorageAccess session;
    private Path tempDir;

    public FabricRegen(CoreMcWorld originalWorld, Region region, Extent target, RegenOptions options) {
        super(originalWorld, region, target, options);
    }

    public static boolean regenerate(CoreMcWorld world, Region region, Extent extent, RegenOptions options) throws Exception {
        return new FabricRegen(world, region, extent, options).regenerate();
    }

    @Override
    protected void runTasks(final BooleanSupplier shouldKeepTicking) {
        while (shouldKeepTicking.getAsBoolean()) {
            if (!freshWorld.getChunkSource().pollTask()) {
                return;
            }
        }
    }

    @Override
    protected boolean prepare() {
        this.originalServerLevel = originalWorld.getWorld();
        this.seed = options.getSeed().orElse(originalServerLevel.getSeed());
        return true;
    }

    @Override
    protected boolean initNewWorld() throws Exception {
        tempDir = Files.createTempDirectory("FastAsyncWorldEditWorldGen");
        LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(tempDir);
        session = levelStorageSource.createAccess(REGEN_WORLD_NAME);

        ResourceKey<Level> dimension = originalServerLevel.dimension();
        final Holder<Biome> singleBiome = resolveSingleBiome(options.getBiomeType());

        freshWorld = Fawe.instance().getQueueHandler().sync((Supplier<ServerLevel>) () -> {
            ServerLevel level = new ServerLevel(
                    originalServerLevel.getServer(),
                    Util.backgroundExecutor(),
                    session,
                    (ServerLevelData) originalServerLevel.getLevelData(),
                    dimension,
                    new LevelStem(
                            originalServerLevel.dimensionTypeRegistration(),
                            originalServerLevel.getChunkSource().getGenerator()
                    ),
                    originalServerLevel.isDebug(),
                    seed,
                    ImmutableList.of(),
                    false
            ) {
                @Override
                public @Nonnull Holder<Biome> getUncachedNoiseBiome(int biomeX, int biomeY, int biomeZ) {
                    if (singleBiome != null) {
                        return singleBiome;
                    }
                    return super.getUncachedNoiseBiome(biomeX, biomeY, biomeZ);
                }

                @Override
                public void save(
                        final ProgressListener progressListener,
                        final boolean flush,
                        final boolean savingDisabled
                ) {
                    // no-op: temp regen world must not touch disk
                }
            };
            level.noSave = true;
            return level;
        }).get();
        return true;
    }

    @Override
    protected void cleanup() {
        try {
            if (session != null) {
                session.close();
            }
        } catch (Exception ignored) {
        }

        if (freshWorld != null) {
            try {
                Fawe.instance().getQueueHandler().sync(() -> {
                    try {
                        freshWorld.getChunkSource().close();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception ignored) {
            }
        }

        if (tempDir != null) {
            try {
                SafeFiles.tryHardToDeleteDir(tempDir);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected IChunkCache<IChunkGet> initSourceQueueCache() {
        FabricFaweAdapter adapter = FabricAdapterBootstrap.getAdapter();
        ServerLevel world = freshWorld;
        return new ChunkCache<>((x, z) -> adapter.getChunk(world, x, z));
    }

    @Nullable
    private Holder<Biome> resolveSingleBiome(@Nullable BiomeType biomeType) {
        if (biomeType == null) {
            return null;
        }
        Identifier id = Identifier.parse(biomeType.id());
        return originalServerLevel.registryAccess()
                .lookupOrThrow(Registries.BIOME)
                .get(id)
                .orElseGet(() -> originalServerLevel.registryAccess()
                        .lookupOrThrow(Registries.BIOME)
                        .get(Identifier.parse(BiomeTypes.PLAINS.id()))
                        .orElseThrow());
    }
}