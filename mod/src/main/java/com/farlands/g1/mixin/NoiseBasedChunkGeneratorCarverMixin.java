package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Integer-subsystem real-coordinate patch (B): carvers.
 *
 * <p>{@code NoiseBasedChunkGenerator.applyCarvers} seeds each carver's RNG with
 * {@code random.setLargeFeatureSeed(seed + index, sourcePos.x(), sourcePos.z())}
 * using the LOCAL int chunk coordinates, so at far real coordinates caves
 * repeat with the local int window. We fold the epoch's chunk offset into the
 * seed. At the origin the offset is 0, so vanilla behavior is bit-identical.</p>
 */
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorCarverMixin {

    @Redirect(method = "applyCarvers",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private void farlands$carverSeed(WorldgenRandom random, long seed, int x, int z) {
        random.setLargeFeatureSeed(seed + FarProjection.epochSeedOffset(), x, z);
    }
}
