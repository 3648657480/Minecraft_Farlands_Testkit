package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): stronghold piece layout reseeds its RNG from the
 * LOCAL chunk coords per try. Fold the epoch chunk offset into the seed.
 */
@Mixin(StrongholdStructure.class)
public class StrongholdStructureRealSeedMixin {

    @Redirect(method = "generatePieces",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private static void farlands$realSeed(WorldgenRandom random, long seed, int x, int z) {
        random.setLargeFeatureSeed(seed + FarProjection.epochSeedOffset(), x, z);
    }
}
