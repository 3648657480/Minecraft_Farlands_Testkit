package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Integer-subsystem real-coordinate patch (B): biome decoration (features).
 *
 * <p>{@code ChunkGenerator.applyBiomeDecoration} derives the feature RNG from
 * {@code setDecorationSeed(levelSeed, originX, originZ)} using LOCAL int chunk
 * coordinates, so at far real coordinates features repeat with the local int
 * window. We fold the epoch's chunk offset into the seed; at the origin the
 * offset is 0, so vanilla behavior is bit-identical.</p>
 */
@Mixin(ChunkGenerator.class)
public class ChunkGeneratorDecorationMixin {

    @Redirect(method = "applyBiomeDecoration",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/WorldgenRandom;setDecorationSeed(JII)J"))
    private long farlands$decorationSeed(WorldgenRandom random, long seed, int x, int z) {
        return random.setDecorationSeed(seed + FarProjection.epochSeedOffset(), x, z);
    }
}
