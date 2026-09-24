package com.farlands.g1.mixin;

import com.farlands.g1.runtime.FarRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.OreVeinifier;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): ore veins seed their per-block RNG from the LOCAL
 * block coords, so ore veins repeat with the local window at far coordinates.
 * Route through {@link FarRandom} to fold the epoch in (no-op at the origin).
 */
@Mixin(OreVeinifier.class)
public class OreVeinifierRealRandomMixin {

    @Redirect(method = "lambda$create$0",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/PositionalRandomFactory;at(III)Lnet/minecraft/util/RandomSource;"))
    private static RandomSource farlands$oreRandom(PositionalRandomFactory factory, int x, int y, int z) {
        return FarRandom.at(factory, x, y, z);
    }
}
