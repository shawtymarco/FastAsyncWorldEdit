package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.FaweCache;
import com.fastasyncworldedit.core.extent.processor.heightmap.HeightMapType;
import com.fastasyncworldedit.core.nbt.FaweCompoundTag;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.biome.BiomeType;
import com.sk89q.worldedit.world.biome.BiomeTypes;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

/**
 * Fabric chunk get/set implementation using direct LevelChunk section access.
 */
public class FabricGetBlocks extends AbstractFabricGetBlocks<ServerLevel, LevelChunk> {

    private final FabricFaweAdapter adapter;
    private LevelChunk chunk;

    public FabricGetBlocks(FabricFaweAdapter adapter, ServerLevel level, int chunkX, int chunkZ) {
        super(level, chunkX, chunkZ, level.getMinY(), level.getMaxY());
        this.adapter = adapter;
    }

    @Override
    protected void send() {
        LevelChunk levelChunk = ensureLoaded(serverLevel).join();
        ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                levelChunk,
                serverLevel.getLightEngine(),
                null,
                null
        );
        for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(new ChunkPos(chunkX, chunkZ), false)) {
            player.connection.send(packet);
        }
    }

    @Override
    protected CompletableFuture<LevelChunk> ensureLoaded(ServerLevel level) {
        LevelChunk existing = level.getChunkSource().getChunk(chunkX, chunkZ, false);
        if (existing != null) {
            this.chunk = existing;
            return CompletableFuture.completedFuture(existing);
        }
        return CompletableFuture.supplyAsync(() -> {
            LevelChunk loaded = (LevelChunk) level.getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
            this.chunk = loaded;
            return loaded;
        }, level.getServer());
    }

    @Override
    protected <T extends Future<T>> T internalCall(
            IChunkSet set,
            Runnable finalizer,
            int copyKey,
            LevelChunk nmsChunk,
            ServerLevel nmsWorld
    ) throws Exception {
        LevelChunkSection[] sections = nmsChunk.getSections();
        for (int layer = set.getMinSectionPosition(); layer <= set.getMaxSectionPosition(); layer++) {
            char[] data = set.loadIfPresent(layer);
            if (data == null) {
                continue;
            }
            int index = layer - (nmsWorld.getMinY() >> 4);
            if (index < 0 || index >= sections.length) {
                continue;
            }
            LevelChunkSection section = sections[index];
            if (section == null) {
                section = nmsChunk.getSection(index);
            }
            for (int i = 0; i < 4096; i++) {
                char ordinal = data[i];
                if (ordinal == BlockTypesCache.ReservedIDs.__RESERVED__) {
                    continue;
                }
                BlockState state = BlockTypesCache.states[ordinal];
                if (state == null) {
                    continue;
                }
                int x = i & 15;
                int y = (i >> 8) & 15;
                int z = (i >> 4) & 15;
                section.setBlockState(x, y, z, adapter.toNative(state), false);
            }
            nmsChunk.markUnsaved();
        }
        // Match PaperweightGetBlocks: notify nearby players after NMS apply.
        send();
        if (finalizer != null) {
            finalizer.run();
        }
        //noinspection unchecked,rawtypes
        return (T) (Future) CompletableFuture.completedFuture(null);
    }

    @Override
    public BaseBlock getFullBlock(int x, int y, int z) {
        return getBlock(x, y, z).toBaseBlock((com.fastasyncworldedit.core.queue.IBlocks) this, x, y, z);
    }


    @Override
    public java.util.Collection<FaweCompoundTag> entities() {
        return java.util.Collections.emptyList();
    }

    @Override
    public java.util.Map<com.sk89q.worldedit.math.BlockVector3, FaweCompoundTag> tiles() {
        return java.util.Collections.emptyMap();
    }
    @Override
    public void removeSectionLighting(int layer, boolean sky) {
        LevelChunk levelChunk = chunk != null ? chunk : ensureLoaded(serverLevel).join();
        SectionPos sectionPos = SectionPos.of(levelChunk.getPos(), layer);
        DataLayer blockLayer = serverLevel.getChunkSource().getLightEngine()
                .getLayerListener(LightLayer.BLOCK).getDataLayerData(sectionPos);
        if (blockLayer != null) {
            synchronized (blockLayer) {
                Arrays.fill(blockLayer.getData(), (byte) 0);
            }
        }
        if (sky) {
            DataLayer skyLayer = serverLevel.getChunkSource().getLightEngine()
                    .getLayerListener(LightLayer.SKY).getDataLayerData(sectionPos);
            if (skyLayer != null) {
                synchronized (skyLayer) {
                    Arrays.fill(skyLayer.getData(), (byte) 0);
                }
            }
        }
    }

    @Override
    public BlockState getBlock(int x, int y, int z) {
        LevelChunk c = chunk != null ? chunk : ensureLoaded(serverLevel).join();
        net.minecraft.world.level.block.state.BlockState nms = c.getBlockState(new BlockPos(x, y, z));
        return adapter.adapt(nms);
    }

    @Override
    public BiomeType getBiomeType(int x, int y, int z) {
        LevelChunk c = chunk != null ? chunk : ensureLoaded(serverLevel).join();
        Holder<Biome> holder = c.getNoiseBiome(x >> 2, y >> 2, z >> 2);
        return holder.unwrapKey()
                .map(key -> BiomeTypes.get(key.identifier().toString()))
                .orElse(BiomeTypes.PLAINS);
    }

    @Override
    public int getSkyLight(int x, int y, int z) {
        return serverLevel.getBrightness(net.minecraft.world.level.LightLayer.SKY, new BlockPos(x, y, z));
    }

    @Override
    public int getEmittedLight(int x, int y, int z) {
        return serverLevel.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, new BlockPos(x, y, z));
    }

    @Override
    public int[] getHeightMap(HeightMapType type) {
        LevelChunk c = chunk != null ? chunk : ensureLoaded(serverLevel).join();
        Heightmap.Types nmsType = switch (type) {
            case MOTION_BLOCKING -> Heightmap.Types.MOTION_BLOCKING;
            case MOTION_BLOCKING_NO_LEAVES -> Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
            case OCEAN_FLOOR -> Heightmap.Types.OCEAN_FLOOR;
            case WORLD_SURFACE -> Heightmap.Types.WORLD_SURFACE;
            default -> Heightmap.Types.WORLD_SURFACE;
        };
        Heightmap map = c.getOrCreateHeightmapUnprimed(nmsType);
        int[] data = new int[256];
        for (int i = 0; i < 256; i++) {
            data[i] = map.getFirstAvailable(i & 15, i >> 4);
        }
        return data;
    }

    @Nullable
    @Override
    public FaweCompoundTag tile(int x, int y, int z) {
        LevelChunk c = chunk != null ? chunk : ensureLoaded(serverLevel).join();
        BlockEntity be = c.getBlockEntity(new BlockPos(x, y, z));
        return be == null ? null : null; // NBT conversion wired via LinOps in full port
    }

    @Nullable
    @Override
    public FaweCompoundTag entity(UUID uuid) {
        return null;
    }

    @Override
    public Set<Entity> getFullEntities() {
        return Collections.emptySet();
    }

    @Override
    public int setCreateCopy(boolean createCopy) {
        this.createCopy = createCopy;
        if (createCopy) {
            return ++copyKey;
        }
        return -1;
    }

    @Override
    public boolean isCreateCopy() {
        return createCopy;
    }

    @Override
    public IChunkGet getCopy(int key) {
        return copies.get(key);
    }

    @Override
    public void setLightingToGet(char[][] lighting, int minSectionPosition, int maxSectionPosition) {
    }

    @Override
    public void setSkyLightingToGet(char[][] lighting, int minSectionPosition, int maxSectionPosition) {
    }

    @Override
    public void setHeightmapToGet(HeightMapType type, int[] data) {
    }

    @Override
    public int getMaxY() {
        return maxHeight - 1;
    }

    @Override
    public int getMinY() {
        return minHeight;
    }

    @Override
    public boolean trim(boolean aggressive) {
        return false;
    }

    @Override
    public boolean trim(boolean aggressive, int layer) {
        return false;
    }

    @Override
    public void lockCall() {
        callLock.lock();
    }

    @Override
    public void unlockCall() {
        callLock.unlock();
    }

    @Override
    public char[] update(int layer, char[] data, boolean aggressive) {
        if (data == null) {
            data = FaweCache.INSTANCE.SECTION_BITS_TO_CHAR.get();
        }
        LevelChunk c = chunk != null ? chunk : ensureLoaded(serverLevel).join();
        int index = layer - (serverLevel.getMinY() >> 4);
        LevelChunkSection[] sections = c.getSections();
        if (index < 0 || index >= sections.length) {
            java.util.Arrays.fill(data, (char) BlockTypesCache.ReservedIDs.AIR);
            return data;
        }
        LevelChunkSection section = sections[index];
        if (section == null || section.hasOnlyAir()) {
            java.util.Arrays.fill(data, (char) BlockTypesCache.ReservedIDs.AIR);
            return data;
        }
        for (int i = 0; i < 4096; i++) {
            int x = i & 15;
            int y = (i >> 8) & 15;
            int z = (i >> 4) & 15;
            data[i] = (char) adapter.adapt(section.getBlockState(x, y, z)).getOrdinal();
        }
        return data;
    }
}
