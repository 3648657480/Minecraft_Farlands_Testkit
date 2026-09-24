package com.farlands.g1.mixin;

import com.farlands.g1.runtime.FarRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): the surface vertical-gradient rule seeds a
 * positional RNG from the LOCAL context block coords, so it repeats with the
 * local window at far coordinates. Route through {@link FarRandom} (no-op at the
 * origin).
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$VerticalGradientConditionSource$1VerticalGradientCondition")
public class SurfaceRulesVerticalGradientMixin {

    @Redirect(method = "compute",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private RandomSource farlands$gradientRandom(PositionalRandomFactory factory, int x, int y, int z) {
        return FarRandom.at(factory, x, y, z);
    }
}
