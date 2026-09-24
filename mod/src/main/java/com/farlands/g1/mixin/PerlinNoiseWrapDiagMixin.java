package com.farlands.g1.mixin;

import com.farlands.g1.runtime.FarWrapExact;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Diagnostic (default off): when {@code -Dfarlands.diag.exactwrap=true}, replace
 * {@code PerlinNoise.wrap} with an exact modulo so a generation diff against the
 * normal (mis-rounding) build isolates the terrain impact of the wrap failure.
 * Off by default -> zero behavior change.
 */
@Mixin(PerlinNoise.class)
public class PerlinNoiseWrapDiagMixin {

    @Inject(method = "wrap", at = @At("HEAD"), cancellable = true)
    private static void farlands$exactWrap(double x, CallbackInfoReturnable<Double> cir) {
        if (!Boolean.getBoolean("farlands.diag.exactwrap")) {
            return;
        }
        // Only the [2^52, 2^53) window can mis-round; outside it the vanilla
        // double wrap is already exact, so keep the fast path.
        double q = x / 3.3554432E7;
        if (q >= 4503599627370496.0 && q < 9007199254740992.0) {
            if (!farlands$logged) {
                farlands$logged = true;
                System.out.println("[FarLands-G1] exactwrap diag active: first in-window wrap at x=" + x);
                System.out.flush();
            }
            cir.setReturnValue(FarWrapExact.exact(x));
        }
    }

    private static volatile boolean farlands$logged = false;
}
