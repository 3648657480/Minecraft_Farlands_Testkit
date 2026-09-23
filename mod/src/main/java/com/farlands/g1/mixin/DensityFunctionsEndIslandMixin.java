package com.farlands.g1.mixin;

import com.farlands.g1.runtime.RealCoords;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integer-subsystem real-coordinate patch (B): the End island density function
 * computes from {@code context.blockX()/8} as an {@code int}, i.e. from the
 * LOCAL coordinate. At far real coordinates that makes the End islands repeat
 * with the local int wedge. This mixin feeds the REAL coordinate
 * ({@link RealCoords#getBlockXDouble()}) into a wide (double) copy of
 * {@code getHeightValue}, matching vanilla's truncation/float semantics so that
 * within the normal int range the result is bit-identical.
 *
 * <p>Target is {@code DensityFunctions$EndIslandDensityFunction} (private static
 * nested). The wide helper mirrors the vanilla algorithm exactly, only widening
 * the section/chunk coordinates to double.</p>
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.DensityFunctions$EndIslandDensityFunction")
public abstract class DensityFunctionsEndIslandMixin {

    @Shadow
    private SimplexNoise islandNoise;

    /** Real-coordinate replacement for the vanilla compute (injected as return override). */
    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void farlands$computeReal(DensityFunction.FunctionContext context,
            CallbackInfoReturnable<Double> cir) {
        if (!(context instanceof RealCoords rc)
            || !com.farlands.g1.util.FarProjection.isEpochActive()) {
            return; // leave vanilla behavior untouched outside the epoch domain
        }
        cir.setReturnValue((farlands$heightWide(this.islandNoise,
            rc.getBlockXDouble() / 8.0, rc.getBlockZDouble() / 8.0) - 8.0) / 128.0);
    }

    /**
     * Wide mirror of {@code EndIslandDensityFunction.getHeightValue}. Truncation
     * toward zero ({@code (long)}) and the float sub-expressions are kept so the
     * result equals vanilla whenever the coordinates are within int range.
     */
    @Unique
    private static float farlands$heightWide(SimplexNoise islandNoise, double sectionXd, double sectionZd) {
        double sectionX = (long) sectionXd;
        double sectionZ = (long) sectionZd;
        double chunkX = (long) (sectionX / 2.0);
        double chunkZ = (long) (sectionZ / 2.0);
        double subSectionX = sectionX - chunkX * 2.0;
        double subSectionZ = sectionZ - chunkZ * 2.0;
        float doffs = 100.0F - Mth.sqrt((float) (sectionX * sectionX + sectionZ * sectionZ)) * 8.0F;
        doffs = Mth.clamp(doffs, -100.0F, 80.0F);

        for (int xo = -12; xo <= 12; xo++) {
            for (int zo = -12; zo <= 12; zo++) {
                double totalChunkX = chunkX + (double) xo;
                double totalChunkZ = chunkZ + (double) zo;
                if (totalChunkX * totalChunkX + totalChunkZ * totalChunkZ > 4096.0
                    && islandNoise.getValue(totalChunkX, totalChunkZ) < -0.9) {
                    float islandSize = (Mth.abs((float) totalChunkX) * 3439.0F
                        + Mth.abs((float) totalChunkZ) * 147.0F) % 13.0F + 9.0F;
                    double xd = subSectionX - (double) (xo * 2);
                    double zd = subSectionZ - (double) (zo * 2);
                    float newDoffs = 100.0F - Mth.sqrt((float) (xd * xd + zd * zd)) * islandSize;
                    newDoffs = Mth.clamp(newDoffs, -100.0F, 80.0F);
                    doffs = Math.max(doffs, newDoffs);
                }
            }
        }
        return doffs;
    }
}
