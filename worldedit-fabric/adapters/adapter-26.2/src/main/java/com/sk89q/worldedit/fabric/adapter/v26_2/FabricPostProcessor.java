package com.sk89q.worldedit.fabric.adapter.v26_2;

import com.fastasyncworldedit.core.extent.processor.ProcessorScope;
import com.fastasyncworldedit.core.queue.IBatchProcessor;
import com.fastasyncworldedit.core.queue.IChunk;
import com.fastasyncworldedit.core.queue.IChunkGet;
import com.fastasyncworldedit.core.queue.IChunkSet;
import com.sk89q.worldedit.extent.Extent;

import javax.annotation.Nullable;

/**
 * Post-edit processor. Fabric currently uses a pass-through; fluid ticking can be added later.
 */
public class FabricPostProcessor implements IBatchProcessor {

    @Override
    public IChunkSet processSet(IChunk chunk, IChunkGet get, IChunkSet set) {
        return set;
    }

    @Nullable
    @Override
    public Extent construct(Extent child) {
        return child;
    }

    @Override
    public ProcessorScope getScope() {
        return ProcessorScope.READING_SET_BLOCKS;
    }
}
