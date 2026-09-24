package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): the 2D surface-rules noise condition samples its
 * NormalNoise from the LOCAL context block coords, so surface-rule decisions
 * repeat with the local window at far coordinates. Redirect to the real
 * coordinate (epoch + local); origin stays bit-identical.
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.SurfaceRules$Context$1")
public class SurfaceRulesContext1NoiseMixin {

    @Redirect(method = "getAsDouble",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$real2d(NormalNoise noise, double x, double y, double z) {
        if (!FarProjection.isEpochActive()) {
            return noise.getValue(x, y, z);
        }
        return noise.getValue(FarProjection.unwrapX((int) x), y, FarProjection.unwrapZ((int) z));
    }
}
