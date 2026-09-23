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

package com.sk89q.worldedit.fabric.internal;

import com.fastasyncworldedit.core.extent.processor.PlacementStateProcessor;
import com.fastasyncworldedit.core.extent.processor.lighting.RelighterFactory;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.sk89q.worldedit.coremc.internal.CoreMcPlatform;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.fabric.adapter.v26_2.FabricAdapterBootstrap;
import com.sk89q.worldedit.function.mask.BlockTypeMask;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.lifecycle.Lifecycled;
import com.sk89q.worldedit.util.lifecycle.SimpleLifecycled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.enginehub.worldeditcui.protocol.CUIPacket;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

class FabricPlatform extends CoreMcPlatform {

    private RelighterFactory relighterFactory;

    private static Lifecycled<MinecraftServer> createMinecraftServerLifecycled() {
        SimpleLifecycled<MinecraftServer> lifecycledServer = SimpleLifecycled.invalid();
        ServerLifecycleEvents.SERVER_STARTING.register(lifecycledServer::newValue);
        ServerLifecycleEvents.SERVER_STOPPING.register(_ -> lifecycledServer.invalidate());
        return lifecycledServer;
    }

    FabricPlatform(FabricWorldEdit mod) {
        super(mod, createMinecraftServerLifecycled());
    }

    @Override
    public FabricAdapter getAdapter() {
        return FabricAdapter.get();
    }

    @Override
    public void sendCUIPacket(ServerPlayer player, CUIPacket packet) {
        try {
            ServerPlayNetworking.send(player, packet);
        } catch (NoClassDefFoundError | IllegalStateException e) {
            // CUI protocol mod not installed or channel not registered
        }
    }

    @Override
    public String getPlatformName() {
        return "Fabric-FAWE";
    }

    @Override
    public String id() {
        return "intellectualsites:fabric";
    }

    // FAWE
    @Override
    public @Nonnull RelighterFactory getRelighterFactory() {
        if (relighterFactory == null) {
            relighterFactory = FabricAdapterBootstrap.getRelighterFactory();
        }
        return relighterFactory;
    }

    @Override
    public int versionMinY() {
        return -64;
    }

    @Override
    public int versionMaxY() {
        return 319;
    }

    @Override
    public IBatchProcessor getPlatformProcessor(boolean fastMode) {
        return FabricAdapterBootstrap.getAdapter().getPlatformProcessor(fastMode);
    }

    @Override
    public IBatchProcessor getPlatformPostProcessor(boolean fastMode) {
        return FabricAdapterBootstrap.getAdapter().getPlatformPostProcessor(fastMode);
    }

    @Override
    public PlacementStateProcessor getPlatformPlacementProcessor(
            Extent extent,
            BlockTypeMask mask,
            @Nullable Region region
    ) {
        return FabricAdapterBootstrap.getAdapter().getPlatformPlacementProcessor(extent, mask, region);
    }
}
