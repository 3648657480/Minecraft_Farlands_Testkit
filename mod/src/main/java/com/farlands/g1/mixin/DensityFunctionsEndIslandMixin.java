package com.farlands.g1.mixin;

import com.farlands.g1.runtime.EndIslandMath;
import com.farlands.g1.runtime.RealCoords;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Integer-subsystem real-coordinate patch (B): the End island density function
 * computes from {@code context.blockX()/8} as an {@code int}, i.e. from the
 * LOCAL coordinate. At far real coordinates that makes the End islands repeat
 * with the local int domain. This mixin feeds the REAL coordinate
 * ({@link RealCoords#getBlockXDouble()}) into {@link EndIslandMath#heightValue},
 * a wide (double) copy of {@code getHeightValue} that matches vanilla's
 * truncation/float semantics so the result is bit-identical within the normal
 * int range.
 *
 * <p>Target is {@code DensityFunctions$EndIslandDensityFunction} (private static
 * nested). Outside the epoch domain the vanilla body runs unchanged.</p>
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
        cir.setReturnValue((EndIslandMath.heightValue(this.islandNoise,
            rc.getBlockXDouble() / 8.0, rc.getBlockZDouble() / 8.0) - 8.0) / 128.0);
    }
}
