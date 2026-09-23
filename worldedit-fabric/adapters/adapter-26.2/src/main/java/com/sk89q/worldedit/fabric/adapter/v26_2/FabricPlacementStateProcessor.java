package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.extent.processor.PlacementStateProcessor;
import com.fastasyncworldedit.core.util.ExtentTraverser;
import com.fastasyncworldedit.core.wrappers.WorldWrapper;
import com.sk89q.worldedit.coremc.internal.CoreMcWorld;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.Vector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Direction;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockState;
import com.sk89q.worldedit.world.block.BlockTypesCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies vanilla block placement rules (stairs, fences, etc.) using {@link Block#getStateForPlacement}.
 */
public class FabricPlacementStateProcessor extends PlacementStateProcessor {

    private final FabricFaweAdapter adapter = FabricAdapterBootstrap.getAdapter();
    private final FabricMutableBlockPlaceContext mutableBlockPlaceContext;
    private final FabricLevelProxy proxyLevel;

    public FabricPlacementStateProcessor(Extent extent, BlockTypeMask mask, Region region) {
        super(extent, mask, region);
        World world = ExtentTraverser.getWorldFromExtent(extent);
        if (world == null) {
            throw new UnsupportedOperationException(
                    "World is required for PlacementStateProcessor but none found in given extent.");
        }
        if (world instanceof WorldWrapper wrapper) {
            world = wrapper.getParent();
        }
        ServerLevel serverLevel = resolveServerLevel(world);
        this.proxyLevel = FabricLevelProxy.getInstance(serverLevel, this);
        this.mutableBlockPlaceContext = new FabricMutableBlockPlaceContext(proxyLevel);
    }

    private FabricPlacementStateProcessor(
            Extent extent,
            BlockTypeMask mask,
            Map<SecondPass, Character> crossChunkSecondPasses,
            ServerLevel serverLevel,
            ThreadLocal<PlacementStateProcessor> threadProcessors,
            Region region,
            AtomicBoolean finished
    ) {
        super(extent, mask, crossChunkSecondPasses, threadProcessors, region, finished);
        this.proxyLevel = FabricLevelProxy.getInstance(serverLevel, this);
        this.mutableBlockPlaceContext = new FabricMutableBlockPlaceContext(proxyLevel);
    }

    private static ServerLevel resolveServerLevel(World world) {
        ServerLevel level = FabricWorlds.resolve(world);
        if (level == null && world instanceof CoreMcWorld coreMcWorld) {
            level = coreMcWorld.getWorld();
        }
        if (level == null) {
            throw new UnsupportedOperationException("Cannot resolve ServerLevel for " + world.getName());
        }
        return level;
    }

    @Override
    protected char getStateAtFor(
            int x,
            int y,
            int z,
            BlockState state,
            Vector3 clickPos,
            Direction clickedFaceDirection,
            BlockVector3 clickedBlock
    ) {
        Block block = adapter.toNative(state).getBlock();
        Vec3 pos = new Vec3(clickPos.x(), clickPos.y(), clickPos.z());
        net.minecraft.core.Direction side = net.minecraft.core.Direction.valueOf(clickedFaceDirection.toString());
        BlockPos blockPos = new BlockPos(clickedBlock.x(), clickedBlock.y(), clickedBlock.z());
        net.minecraft.world.level.block.state.BlockState newState = block.getStateForPlacement(
                mutableBlockPlaceContext.withSetting(
                        new BlockHitResult(pos, side, blockPos, false),
                        side.getOpposite()
                )
        );
        return newState == null
                ? BlockTypesCache.ReservedIDs.AIR
                : adapter.ibdIDToOrdinal(Block.BLOCK_STATE_REGISTRY.getId(newState));
    }

    @Override
    @Nullable
    public Extent construct(Extent child) {
        if (child == getExtent()) {
            return this;
        }
        return new FabricPlacementStateProcessor(child, mask, region);
    }

    @Override
    public PlacementStateProcessor fork() {
        return new FabricPlacementStateProcessor(
                extent,
                mask,
                postCompleteSecondPasses,
                proxyLevel.serverLevel,
                threadProcessors,
                region,
                finished
        );
    }
}