package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.extent.processor.lighting.NullRelighter;
import com.fastasyncworldedit.core.extent.processor.lighting.RelightMode;
import com.fastasyncworldedit.core.extent.processor.lighting.Relighter;
import com.fastasyncworldedit.core.extent.processor.lighting.RelighterFactory;
import com.fastasyncworldedit.core.queue.IQueueExtent;
import com.sk89q.worldedit.world.World;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nonnull;

public class FabricStarlightRelighterFactory implements RelighterFactory {

    public static final FabricStarlightRelighterFactory INSTANCE = new FabricStarlightRelighterFactory();

    @Override
    public @Nonnull Relighter createRelighter(RelightMode relightMode, World world, IQueueExtent<?> queue) {
        ServerLevel level = FabricWorlds.resolve(world);
        if (level == null) {
            return NullRelighter.INSTANCE;
        }
        return new FabricStarlightRelighter(level, queue);
    }
}
