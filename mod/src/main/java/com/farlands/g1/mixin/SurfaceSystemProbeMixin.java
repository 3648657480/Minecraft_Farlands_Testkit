package com.farlands.g1.mixin;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Headless probe: logs the surface stage's per-column context values for
 * the first column of each chunk, to trace why the surface rules place
 * nothing beyond +/-2^31.
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemProbeMixin {

    private static int farlands$chunks = 0;

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void farlands$probe(RandomState randomState,
            net.minecraft.world.level.biome.BiomeManager biomeManager, boolean useLegacyRandom,
            WorldGenerationContext generationContext, ChunkAccess chunk, NoiseChunk noiseChunk,
            SurfaceRules.RuleSource ruleSource, Set<?> possibleBiomes, CallbackInfo ci) {
        if (System.getProperty("farlands.testgen") == null) return;
        farlands$chunks++;
        if (farlands$chunks > 300) return;
        int minBlockX = chunk.getPos().getMinBlockX();
        int minBlockZ = chunk.getPos().getMinBlockZ();
        int surfWG = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, 0, 0);
        int pls = noiseChunk.preliminarySurfaceLevel(minBlockX, minBlockZ);
        System.out.println("[FarLands-Surface] chunk=(" + chunk.getPos().x() + "," + chunk.getPos().z()
            + ") minBlockX=" + minBlockX + " surfWG(0,0)=" + surfWG + " prelimSurface=" + pls);
        System.out.flush();
    }
}
