package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.util.ReflectionUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import sun.misc.Unsafe;

import javax.annotation.Nonnull;

/**
 * Lightweight {@link ServerLevel} proxy that reads pending edits from a {@link FabricPlacementStateProcessor}.
 */
public final class FabricLevelProxy extends ServerLevel {

    ServerLevel serverLevel;
    private FabricPlacementStateProcessor processor;
    private FabricFaweAdapter adapter;

    @SuppressWarnings("DataFlowIssue")
    private FabricLevelProxy() {
        super(null, null, null, null, null, null, false, 0L, null, false);
        throw new IllegalStateException("Cannot be instantiated");
    }

    public static FabricLevelProxy getInstance(ServerLevel serverLevel, FabricPlacementStateProcessor processor) {
        Unsafe unsafe = ReflectionUtils.getUnsafe();
        FabricLevelProxy newLevel;
        try {
            newLevel = (FabricLevelProxy) unsafe.allocateInstance(FabricLevelProxy.class);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
        newLevel.processor = processor;
        newLevel.adapter = FabricAdapterBootstrap.getAdapter();
        newLevel.serverLevel = serverLevel;
        return newLevel;
    }

    @Override
    @Nonnull
    public BlockState getBlockState(@Nonnull BlockPos blockPos) {
        if (blockPos.getX() == Integer.MAX_VALUE) {
            return Blocks.AIR.defaultBlockState();
        }
        com.sk89q.worldedit.world.block.BlockState state = processor.getBlockStateAt(
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ()
        );
        return adapter.toNative(state);
    }

    @Override
    @Nonnull
    public FluidState getFluidState(@Nonnull BlockPos pos) {
        if (pos.getX() == Integer.MAX_VALUE) {
            return Fluids.EMPTY.defaultFluidState();
        }
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean isWaterAt(@Nonnull BlockPos pos) {
        if (pos.getX() == Integer.MAX_VALUE) {
            return false;
        }
        return getBlockState(pos).getFluidState().is(FluidTags.WATER);
    }

    @Override
    public int getHeight() {
        return serverLevel.getHeight();
    }

    @Override
    public int getMinY() {
        return serverLevel.getMinY();
    }

    @Override
    public int getMaxY() {
        return serverLevel.getMaxY();
    }

    @Override
    public boolean isInsideBuildHeight(int blockY) {
        return serverLevel.isInsideBuildHeight(blockY);
    }

    @Override
    public boolean isOutsideBuildHeight(BlockPos pos) {
        return serverLevel.isOutsideBuildHeight(pos);
    }

    @Override
    public boolean isOutsideBuildHeight(int blockY) {
        return serverLevel.isOutsideBuildHeight(blockY);
    }

    @Override
    public WorldBorder getWorldBorder() {
        return serverLevel.getWorldBorder();
    }
}