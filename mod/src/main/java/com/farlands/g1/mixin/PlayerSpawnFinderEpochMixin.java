package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.PlayerSpawnFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/**
 * E line: the per-chunk spawn search (PlayerSpawnFinder) forces chunk
 * generation one chunk at a time; under an active epoch the generated
 * task's cache-neighbor acquire misses the not-yet-loaded neighbors
 * (acquireGeneration NPE at world load). Skip the search entirely: the
 * respawn point IS the spawn in the local domain.
 */
@Mixin(PlayerSpawnFinder.class)
public class PlayerSpawnFinderEpochMixin {

    @Inject(method = "getSpawnPosInChunk", at = @At("HEAD"), cancellable = true)
    private static void farlands$skipSearch(ServerLevel level, ChunkPos chunkPos,
            CallbackInfoReturnable<BlockPos> cir) {
        if (FarProjection.isEpochActive()) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "findSpawn", at = @At("HEAD"), cancellable = true)
    private static void farlands$localFindSpawn(ServerLevel level, BlockPos spawnSuggestion,
            CallbackInfoReturnable<CompletableFuture<net.minecraft.world.phys.Vec3>> cir) {
        if (FarProjection.isEpochActive()) {
            cir.setReturnValue(CompletableFuture.completedFuture(
                new net.minecraft.world.phys.Vec3(0.5, 100.0, 0.5)));
        }
    }
}
