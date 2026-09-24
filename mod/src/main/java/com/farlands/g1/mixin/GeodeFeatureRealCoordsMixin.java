package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): {@code GeodeFeature.place} samples its geode
 * noise at the raw BlockPos (local) coordinates, so geodes repeat with the local
 * window at far coordinates. Redirect to the real coordinate (epoch + local);
 * no-op at the origin.
 */
@Mixin(GeodeFeature.class)
public class GeodeFeatureRealCoordsMixin {

    @Redirect(method = "place",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$realGeodeNoise(NormalNoise noise, double x, double y, double z) {
        if (!FarProjection.isEpochActive()) {
            return noise.getValue(x, y, z);
        }
        return noise.getValue(FarProjection.unwrapX((int) x), y, FarProjection.unwrapZ((int) z));
    }
}
