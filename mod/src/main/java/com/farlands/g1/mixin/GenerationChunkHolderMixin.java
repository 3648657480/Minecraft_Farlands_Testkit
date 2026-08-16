package com.farlands.g1.mixin;

import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.concurrent.atomic.AtomicReference;

@Mixin(GenerationChunkHolder.class)
public class GenerationChunkHolderMixin {

    @Shadow
    private AtomicReference<ChunkStatus> startedWork;

    @Shadow
    private java.util.concurrent.atomic.AtomicReferenceArray<Object> futures;

    @Overwrite
    private boolean acquireStatusBump(final ChunkStatus status) {
        ChunkStatus parent = status == ChunkStatus.EMPTY ? null : status.getParent();
        ChunkStatus previousStarted = this.startedWork.compareAndExchange(parent, status);
        if (previousStarted == parent) {
            return true;
        } else if (previousStarted != null && !status.isAfter(previousStarted)) {
            return false;
        } else {
            // Out-of-order: chunk is behind the expected parent.
            // Skip the bump and return false to reuse the existing future.
            return false;
        }
    }
}
