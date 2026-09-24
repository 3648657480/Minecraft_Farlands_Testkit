package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): {@code Structure.GenerationContext.makeRandom}
 * seeds the structure RNG from the LOCAL chunk coords, so structures repeat with
 * the local window at far coordinates. Fold the epoch chunk offset into the
 * seed (0 at the origin -> bit-identical).
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.Structure$GenerationContext")
public class StructureGenerationContextRealSeedMixin {

    @Redirect(method = "makeRandom",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private static void farlands$realSeed(WorldgenRandom random, long seed, int x, int z) {
        random.setLargeFeatureSeed(seed + FarProjection.epochSeedOffset(), x, z);
    }
}
