package com.farlands.g1.runtime;

import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * B line: wide mirror of {@code DensityFunctions.EndIslandDensityFunction}'s
 * private {@code getHeightValue}. The vanilla method takes {@code int} section
 * coordinates (block/8), so at far real coordinates the End islands repeat with
 * the local int domain. This version takes the real section coordinate as a
 * double and keeps the arithmetic wide.
 *
 * <p>Vanilla reachability: {@code section = blockX/8} with {@code blockX} an
 * {@code int}, so {@code |section| <= 2^28}. Within that range the dispatcher
 * calls {@link #vanillaHeightValue}, i.e. the exact vanilla body (including
 * Java's {@code int} square overflow, which already triggers at
 * {@code section ~ 32768}). This guarantees bit-identical output for every
 * coordinate vanilla can actually generate. The End world border (30M blocks ->
 * section 3.75M) lies inside this range, so the whole vanilla End is covered.
 * Beyond it the {@link #wideHeightValue} path runs in double.</p>
 */
public final class EndIslandMath {

    private EndIslandMath() {
    }

    /** Real-coordinate entry point: {@code sectionX = realBlockX / 8}. */
    public static float heightValue(SimplexNoise islandNoise, double sectionXd, double sectionZd) {
        if (Double.isFinite(sectionXd) && Double.isFinite(sectionZd)) {
            long sx = (long) sectionXd; // Java truncation toward zero, matching int division
            long sz = (long) sectionZd;
            if (sx >= -(1L << 28) && sx <= (1L << 28)
                && sz >= -(1L << 28) && sz <= (1L << 28)) {
                return vanillaHeightValue(islandNoise, (int) sx, (int) sz);
            }
        }
        return wideHeightValue(islandNoise, sectionXd, sectionZd);
    }

    /**
     * Verbatim copy of the vanilla {@code EndIslandDensityFunction.getHeightValue}
     * (26.2 trusted source). Also the numeric probe's reference.
     */
    public static float vanillaHeightValue(SimplexNoise islandNoise, int sectionX, int sectionZ) {
        int chunkX = sectionX / 2;
        int chunkZ = sectionZ / 2;
        int subSectionX = sectionX % 2;
        int subSectionZ = sectionZ % 2;
        float doffs = 100.0F - Mth.sqrt(sectionX * sectionX + sectionZ * sectionZ) * 8.0F;
        doffs = Mth.clamp(doffs, -100.0F, 80.0F);

        for (int xo = -12; xo <= 12; xo++) {
            for (int zo = -12; zo <= 12; zo++) {
                long totalChunkX = chunkX + xo;
                long totalChunkZ = chunkZ + zo;
                if (totalChunkX * totalChunkX + totalChunkZ * totalChunkZ > 4096L
                    && islandNoise.getValue(totalChunkX, totalChunkZ) < -0.9F) {
                    float islandSize = (Mth.abs((float) totalChunkX) * 3439.0F
                        + Mth.abs((float) totalChunkZ) * 147.0F) % 13.0F + 9.0F;
                    float xd = subSectionX - xo * 2;
                    float zd = subSectionZ - zo * 2;
                    float newDoffs = 100.0F - Mth.sqrt(xd * xd + zd * zd) * islandSize;
                    newDoffs = Mth.clamp(newDoffs, -100.0F, 80.0F);
                    doffs = Math.max(doffs, newDoffs);
                }
            }
        }
        return doffs;
    }

    /**
     * Wide path: identical structure to vanilla but the section/chunk
     * coordinates stay in {@code double} (no {@code int} overflow). Truncation
     * toward zero ({@code (long)}) matches integer division; the float
     * sub-expressions keep vanilla's width/order.
     */
    public static float wideHeightValue(SimplexNoise islandNoise, double sectionXd, double sectionZd) {
        double sectionX = (long) sectionXd;
        double sectionZ = (long) sectionZd;
        double chunkX = (long) (sectionX / 2.0);
        double chunkZ = (long) (sectionZ / 2.0);
        double subSectionX = sectionX - chunkX * 2.0;
        double subSectionZ = sectionZ - chunkZ * 2.0;
        float doffs = 100.0F - Mth.sqrt((float) (sectionX * sectionX + sectionZ * sectionZ)) * 8.0F;
        doffs = Mth.clamp(doffs, -100.0F, 80.0F);

        for (int xo = -12; xo <= 12; xo++) {
            for (int zo = -12; zo <= 12; zo++) {
                double totalChunkX = chunkX + (double) xo;
                double totalChunkZ = chunkZ + (double) zo;
                if (totalChunkX * totalChunkX + totalChunkZ * totalChunkZ > 4096.0
                    && islandNoise.getValue(totalChunkX, totalChunkZ) < -0.9) {
                    float islandSize = (Mth.abs((float) totalChunkX) * 3439.0F
                        + Mth.abs((float) totalChunkZ) * 147.0F) % 13.0F + 9.0F;
                    double xd = subSectionX - (double) (xo * 2);
                    double zd = subSectionZ - (double) (zo * 2);
                    float newDoffs = 100.0F - Mth.sqrt((float) (xd * xd + zd * zd)) * islandSize;
                    newDoffs = Mth.clamp(newDoffs, -100.0F, 80.0F);
                    doffs = Math.max(doffs, newDoffs);
                }
            }
        }
        return doffs;
    }
}
