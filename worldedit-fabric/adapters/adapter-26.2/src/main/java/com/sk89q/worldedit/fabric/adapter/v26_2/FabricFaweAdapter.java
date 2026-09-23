package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.extent.processor.PlacementStateProcessor;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.coremc.internal.CoreMcPlatform;
import com.sk89q.worldedit.coremc.internal.CoreMcTransmogrifier;
import com.sk89q.worldedit.extension.platform.Capability;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Fabric FAWE adapter for Minecraft 26.2 — block id maps + chunk getters.
 */
public class FabricFaweAdapter {

    private int[] ibdToOrdinal = new int[0];
    private int[] ordinalToIbdID = new int[0];
    private final Map<net.minecraft.world.level.block.state.BlockState, BlockState> blockStateCache = new IdentityHashMap<>();
    private boolean initialised;

    public FabricFaweAdapter() {
        // Defer ensureInit until after PlatformsRegistered + setupRegistries.
    }

    private static CoreMcTransmogrifier transmogrifier() {
        return ((CoreMcPlatform) WorldEdit.getInstance().getPlatformManager()
                .queryCapability(Capability.WORLD_EDITING)).getTransmogrifier();
    }

    public synchronized void ensureInit() {
        if (initialised) {
            return;
        }
        int maxId = Block.BLOCK_STATE_REGISTRY.size();
        ibdToOrdinal = new int[Math.max(maxId + 1, 1)];
        ordinalToIbdID = new int[BlockTypesCache.states.length];
        Arrays.fill(ibdToOrdinal, BlockTypesCache.ReservedIDs.AIR);

        CoreMcTransmogrifier transmogrifier = transmogrifier();
        // Map every WorldEdit state via transmogrifier (FAWE internalId != NMS registry id).
        for (int ordinal = 0; ordinal < BlockTypesCache.states.length; ordinal++) {
            BlockState state = BlockTypesCache.states[ordinal];
            if (state == null) {
                continue;
            }
            net.minecraft.world.level.block.state.BlockState nms = transmogrifier.transmogToMinecraft(state);
            int id = Block.BLOCK_STATE_REGISTRY.getId(nms);
            if (id < 0) {
                continue;
            }
            if (id >= ibdToOrdinal.length) {
                int[] grown = new int[id + 1];
                Arrays.fill(grown, BlockTypesCache.ReservedIDs.AIR);
                System.arraycopy(ibdToOrdinal, 0, grown, 0, ibdToOrdinal.length);
                ibdToOrdinal = grown;
            }
            ibdToOrdinal[id] = ordinal;
            ordinalToIbdID[ordinal] = id;
            blockStateCache.put(nms, state);
        }
        initialised = true;
    }

    public int[] getIbdToOrdinal() {
        ensureInit();
        return ibdToOrdinal;
    }

    public int[] getOrdinalToIbdID() {
        ensureInit();
        return ordinalToIbdID;
    }

    public BlockState adapt(net.minecraft.world.level.block.state.BlockState nms) {
        ensureInit();
        BlockState cached = blockStateCache.get(nms);
        if (cached != null) {
            return cached;
        }
        BlockState adapted = transmogrifier().transmogToWorldEdit(nms);
        blockStateCache.put(nms, adapted);
        return adapted != null ? adapted : BlockTypes.AIR.getDefaultState();
    }

    public net.minecraft.world.level.block.state.BlockState toNative(BlockState state) {
        ensureInit();
        int ordinal = state.getOrdinal();
        if (ordinal >= 0 && ordinal < ordinalToIbdID.length) {
            int ibd = ordinalToIbdID[ordinal];
            // Unmapped entries stay 0 (air). Only trust that for actual air states.
            if (ibd != 0 || state.getMaterial().isAir()) {
                net.minecraft.world.level.block.state.BlockState nms = Block.BLOCK_STATE_REGISTRY.byId(ibd);
                if (nms != null) {
                    return nms;
                }
            }
        }
        return transmogrifier().transmogToMinecraft(state);
    }

    public IChunkGet get(World world, int chunkX, int chunkZ) {
        ServerLevel level = FabricWorlds.resolve(world);
        if (level == null) {
            throw new IllegalStateException("Cannot resolve ServerLevel for " + world.getName());
        }
        return getChunk(level, chunkX, chunkZ);
    }

    public IChunkGet getChunk(ServerLevel level, int chunkX, int chunkZ) {
        return new FabricGetBlocks(this, level, chunkX, chunkZ);
    }

    public char ibdIDToOrdinal(int id) {
        ensureInit();
        if (id < 0 || id >= ibdToOrdinal.length) {
            return BlockTypesCache.ReservedIDs.AIR;
        }
        return (char) ibdToOrdinal[id];
    }

    public IBatchProcessor getPlatformProcessor(boolean fastMode) {
        return null;
    }

    public IBatchProcessor getPlatformPostProcessor(boolean fastMode) {
        return fastMode ? null : new FabricPostProcessor();
    }

    public PlacementStateProcessor getPlatformPlacementProcessor(
            Extent extent,
            BlockTypeMask mask,
            @Nullable Region region
    ) {
        return new FabricPlacementStateProcessor(extent, mask, region);
    }
}
