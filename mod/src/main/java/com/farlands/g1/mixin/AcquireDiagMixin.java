package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic: print the acquire key when the holder is missing, and a small
 * sample of what IS in the map, so the key-domain mismatch is visible.
 */
@Mixin(ChunkMap.class)
public abstract class AcquireDiagMixin {

    @Shadow
    @Final
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;

    @Inject(method = "acquireGeneration", at = @At("HEAD"))
    private void farlands$diag(long chunkNode, CallbackInfoReturnable<GenerationChunkHolder> cir) {
        if (!FarProjection.isEpochActive()) return;
        if (this.updatingChunkMap.get(chunkNode) == null) {
            System.out.println("[Acq] MISS key=(" + ChunkPos.getX(chunkNode) + ","
                + ChunkPos.getZ(chunkNode) + ") mapSize=" + this.updatingChunkMap.size());
            String sample = this.updatingChunkMap.keySet().stream().limit(6)
                .map(k -> ChunkPos.getX(k) + "," + ChunkPos.getZ(k)).reduce((a, b) -> a + " " + b).orElse("-");
            System.out.println("[Acq]   map sample=[" + sample + "]");
            System.out.flush();
        }
    }

    @Inject(method = "scheduleGenerationTask", at = @At("HEAD"))
    private void farlands$diagCenter(ChunkStatus targetStatus, ChunkPos pos,
            CallbackInfoReturnable<ChunkGenerationTask> cir) {
        if (!FarProjection.isEpochActive()) return;
        System.out.println("[GenTask] center=(" + pos.x() + "," + pos.z() + ") status=" + targetStatus);
        System.out.flush();
    }
}
