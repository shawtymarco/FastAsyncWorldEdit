/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.coremc.internal;

import com.fastasyncworldedit.core.util.TaskManager;
import com.sk89q.worldedit.internal.wna.WorldNativeAccess;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.storage.TagValueInput;
import org.enginehub.linbus.tree.LinCompoundTag;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

public final class CoreMcWorldNativeAccess implements WorldNativeAccess<LevelChunk, BlockState, BlockPos> {
    private final CoreMcPlatform platform;
    private final WeakReference<ServerLevel> world;
    private final Set<Long> dirtyChunks = ConcurrentHashMap.newKeySet();
    private SideEffectSet sideEffectSet;

    public CoreMcWorldNativeAccess(CoreMcPlatform platform, WeakReference<ServerLevel> world) {
        this.platform = platform;
        this.world = world;
    }

    private ServerLevel getWorld() {
        return Objects.requireNonNull(world.get(), "The reference to the world was lost");
    }

    @Override
    public void setCurrentSideEffectSet(SideEffectSet sideEffectSet) {
        this.sideEffectSet = sideEffectSet;
    }

    @Override
    public LevelChunk getChunk(int x, int z) {
        return getWorld().getChunk(x, z);
    }

    @Override
    public BlockState toNative(com.sk89q.worldedit.world.block.BlockState state) {
        // FAWE internalId is not an NMS registry id — use the platform transmogrifier.
        return platform.getAdapter().toNativeBlockState(state);
    }

    @Override
    public BlockState getBlockState(LevelChunk chunk, BlockPos position) {
        return chunk.getBlockState(position);
    }

    @Nullable
    @Override
    public BlockState setBlockState(LevelChunk chunk, BlockPos position, BlockState state) {
        int flags = 0;
        if (sideEffectSet != null) {
            if (!sideEffectSet.shouldApply(SideEffect.UPDATE)) {
                // We don't skip block entity side-effects as that's likely to cause problems.
                flags |= Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE;
            }
        }
        BlockState previous = chunk.setBlockState(position, state, flags);
        if (previous != null) {
            ChunkPos pos = chunk.getPos();
            dirtyChunks.add((((long) pos.x()) << 32) | (pos.z() & 0xffffffffL));
        }
        return previous;
    }

    @Override
    public BlockState getValidBlockForPosition(BlockState block, BlockPos position) {
        return Block.updateFromNeighbourShapes(block, getWorld(), position);
    }

    @Override
    public BlockPos getPosition(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    @Override
    public void updateLightingForBlock(BlockPos position) {
        getWorld().getChunkSource().getLightEngine().checkBlock(position);
    }

    @Override
    public boolean updateTileEntity(BlockPos position, LinCompoundTag tag) {
        net.minecraft.nbt.CompoundTag nativeTag = NBTConverter.toNative(tag);
        ServerLevel level = getWorld();
        BlockEntity tileEntity = level.getChunkAt(position).getBlockEntity(position);
        if (tileEntity == null) {
            return false;
        }
        return CoreMcLoggingProblemReporter.with(
            () -> "loading tile entity at " + position,
            reporter -> {
                var tagValueInput = TagValueInput.create(reporter, level.registryAccess(), nativeTag);
                tileEntity.loadWithComponents(tagValueInput);
                tileEntity.setChanged();
                return true;
            }
        );
    }

    @Override
    public void notifyBlockUpdate(LevelChunk chunk, BlockPos position, BlockState oldState, BlockState newState) {
        if (chunk.getSections()[getWorld().getSectionIndex(position.getY())] != null) {
            getWorld().sendBlockUpdated(position, oldState, newState, Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public boolean isChunkTicking(LevelChunk chunk) {
        return chunk.getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING);
    }

    @Override
    public void markBlockChanged(LevelChunk chunk, BlockPos position) {
        if (chunk.getSections()[getWorld().getSectionIndex(position.getY())] != null) {
            getWorld().getChunkSource().blockChanged(position);
        }
    }

    @Override
    public void notifyNeighbors(BlockPos pos, BlockState oldState, BlockState newState) {
        ServerLevel world = getWorld();
        if (sideEffectSet.shouldApply(SideEffect.EVENTS)) {
            world.updateNeighborsAt(pos, oldState.getBlock());
        } else {
            // Bypasses events currently, watch for changes...
            world.updateNeighborsAt(pos, oldState.getBlock(), ExperimentalRedstoneUtils.initialOrientation(
                world, null, null
            ));
        }
        if (newState.hasAnalogOutputSignal()) {
            world.updateNeighbourForOutputSignal(pos, newState.getBlock());
        }
    }

    @Override
    public void updateBlock(BlockPos pos, BlockState oldState, BlockState newState) {
        ServerLevel world = getWorld();
        newState.onPlace(world, pos, oldState, false);
    }

    @Override
    public void updateNeighbors(BlockPos pos, BlockState oldState, BlockState newState, int recursionLimit) {
        ServerLevel world = getWorld();
        oldState.affectNeighborsAfterRemoval(world, pos, false);
        oldState.updateIndirectNeighbourShapes(world, pos, Block.UPDATE_CLIENTS, recursionLimit);
        newState.updateNeighbourShapes(world, pos, Block.UPDATE_CLIENTS, recursionLimit);
        newState.updateIndirectNeighbourShapes(world, pos, Block.UPDATE_CLIENTS, recursionLimit);
    }

    @Override
    public void onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState) {
        getWorld().updatePOIOnBlockStateChange(pos, oldState, newState);
        platform.extraOnBlockStateChange(getWorld(), pos, oldState, newState);
    }

    @Override
    public void flush() {
        if (dirtyChunks.isEmpty()) {
            return;
        }
        ServerLevel level = getWorld();
        final Set<Long> toSend = Set.copyOf(dirtyChunks);
        dirtyChunks.clear();
        Runnable sendChunks = () -> {
            for (long packed : toSend) {
                int chunkX = (int) (packed >> 32);
                int chunkZ = (int) packed;
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }
                ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                    chunk,
                    level.getLightEngine(),
                    null,
                    null
                );
                for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(chunkX, chunkZ), false)) {
                    player.connection.send(packet);
                }
            }
        };
        if (level.getServer().isSameThread()) {
            sendChunks.run();
        } else {
            TaskManager.taskManager().sync(() -> {
                sendChunks.run();
                return null;
            });
        }
    }
}
