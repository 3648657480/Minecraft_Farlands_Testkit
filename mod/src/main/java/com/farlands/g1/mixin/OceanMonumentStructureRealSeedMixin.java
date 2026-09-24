package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): ocean monument re-load layout seeding uses the
 * LOCAL chunk coords. Fold the epoch chunk offset into the seed.
 */
@Mixin(OceanMonumentStructure.class)
public class OceanMonumentStructureRealSeedMixin {

    @Redirect(method = "regeneratePiecesAfterLoad",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setLargeFeatureSeed(JII)V"))
    private static void farlands$realSeed(WorldgenRandom random, long seed, int x, int z) {
        random.setLargeFeatureSeed(seed + FarProjection.epochSeedOffset(), x, z);
    }
}
