package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E line: when the epoch is active, the world spawn search samples the
 * epoch coordinate, which beyond int range collapses to a huge/derived
 * position and sends the spawn chunk key out of the local domain (observed
 * acquireGeneration failures at 1e306). The spawn must stay in the LOCAL
 * domain - the world origin IS the spawn. Return the origin.
 */
@Mixin(Climate.Sampler.class)
public class SpawnFinderEpochMixin {

    @Inject(method = "findSpawnPosition", at = @At("HEAD"), cancellable = true)
    private void farlands$localSpawn(CallbackInfoReturnable<BlockPos> cir) {
        if (FarProjection.isEpochActive()) {
            cir.setReturnValue(BlockPos.ZERO);
        }
    }
}
