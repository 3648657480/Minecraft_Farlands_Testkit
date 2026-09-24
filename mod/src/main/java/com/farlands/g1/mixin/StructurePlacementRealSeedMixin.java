package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): structure placement probability reducers seed
 * their RNG from the LOCAL chunk coords, so structure placement repeats with the
 * local window at far coordinates. Fold the epoch chunk offset into the seed
 * (0 at the origin -> bit-identical).
 */
@Mixin(StructurePlacement.class)
public class StructurePlacementRealSeedMixin {

    @Redirect(method = "probabilityReducer",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"))
    private static void farlands$salt1(WorldgenRandom random, long seed, int x, int z, int blend) {
        random.setLargeFeatureWithSalt(seed + FarProjection.epochSeedOffset(), x, z, blend);
    }

    @Redirect(method = "legacyProbabilityReducerWithDouble",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private static void farlands$seed(WorldgenRandom random, long seed, int x, int z) {
        random.setLargeFeatureSeed(seed + FarProjection.epochSeedOffset(), x, z);
    }

    @Redirect(method = "legacyArbitrarySaltProbabilityReducer",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureWithSalt(JIII)V"))
    private static void farlands$salt2(WorldgenRandom random, long seed, int x, int z, int blend) {
        random.setLargeFeatureWithSalt(seed + FarProjection.epochSeedOffset(), x, z, blend);
    }
}
