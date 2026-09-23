package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.sk89q.worldedit.coremc.internal.CoreMcWorld;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

import javax.annotation.Nullable;

public final class FabricWorlds {

    private static volatile MinecraftServer server;

    private FabricWorlds() {
    }

    public static void setServer(MinecraftServer minecraftServer) {
        server = minecraftServer;
    }

    @Nullable
    public static ServerLevel resolve(World world) {
        if (world instanceof CoreMcWorld coreMcWorld) {
            return coreMcWorld.getWorld();
        }
        MinecraftServer s = server;
        if (s == null) {
            return null;
        }
        for (ServerLevel level : s.getAllLevels()) {
            if (((ServerLevelData) level.getLevelData()).getLevelName().equals(world.getName())) {
                return level;
            }
        }
        return null;
    }
}
