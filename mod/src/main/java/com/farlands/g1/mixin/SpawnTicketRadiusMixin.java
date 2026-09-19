package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * E line: the player-spawn ticket radius (3) is smaller than the generation
 * dependency radius (RADIUS_AROUND_FULL_CHUNK, ~11). While the spawn area
 * is still being generated the cache-neighbor acquire misses the
 * not-yet-loaded neighbors (acquireGeneration NPE in
 * PrepareSpawnTask$Preparing.tick). Widen the radius to the full
 * dependency radius + 1 when the epoch is active so all generation
 * dependencies load. Applied on the method itself (the caller site is
 * inside a lambda).
 */
@Mixin(ServerChunkCache.class)
public class SpawnTicketRadiusMixin {

    @ModifyVariable(method = "addTicketAndLoadWithRadius", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int farlands$widerSpawnTicket(int radius) {
        if (FarProjection.isEpochActive()) {
            int min = ChunkLevel.RADIUS_AROUND_FULL_CHUNK + 1;
            if (radius < min) {
                System.out.println("[FarLands] ticket radius " + radius + " -> " + min);
                System.out.flush();
                return min;
            }
        }
        return radius;
    }
}
