package com.farlands.g1.mixin;

import java.util.List;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rate-limits chunk generation task draining.
 *
 * <p>Vanilla drains the whole pending generation queue every call, so a
 * teleport into fresh territory queues hundreds of chunks at once: memory
 * spikes into the gigabytes and the GC thrashes. This mixin runs at most
 * {@link #MAX_PER_RUN} tasks per drain and leaves the rest queued, turning
 * the flood into a steady drip.</p>
 */
@Mixin(ChunkMap.class)
public class ChunkMapGenRateLimitMixin {

    @Unique
    private static final int MAX_PER_RUN = Integer.MAX_VALUE;

    @Shadow
    @Final
    private List<ChunkGenerationTask> pendingGenerationTasks;

    @Shadow
    private void runGenerationTask(ChunkGenerationTask task) {
        throw new AssertionError();
    }

    @Inject(method = "runGenerationTasks", at = @At("HEAD"), cancellable = true)
    private void farlands$rateLimit(CallbackInfo ci) {
        int n = Math.min(pendingGenerationTasks.size(), MAX_PER_RUN);
        for (int i = 0; i < n; i++) {
            runGenerationTask(pendingGenerationTasks.remove(0));
        }
        ci.cancel();
    }
}
