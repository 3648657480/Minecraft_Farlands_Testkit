package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * B line (integer subsystems): the surface-material stage samples its
 * {@link NormalNoise} instances with the WORLD block coordinates
 * ({@code minBlockX + local}), which in the epoch engine are LOCAL. At far real
 * coordinates the grass/sand surface, clay bands, badlands and iceberg patterns
 * therefore repeat with the local window, while the density-based terrain shape
 * (already real-coordinate-ized) breaks. This redirects those noise calls to the
 * real coordinate (epoch + local), keeping the origin bit-identical.
 *
 * <p>The x/z arguments at the call site are already multiplied by the noise's
 * horizontal scale (1.0 for surface/clay, 0.2 &amp; 0.75 for badlands, 1.28 &amp;
 * 1.17 for iceberg), so the real value is {@code x + epoch * scale}.</p>
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemRealCoordsMixin {

    @Shadow private NormalNoise surfaceNoise;
    @Shadow private NormalNoise surfaceSecondaryNoise;
    @Shadow private NormalNoise badlandsSurfaceNoise;
    @Shadow private NormalNoise badlandsPillarNoise;
    @Shadow private NormalNoise badlandsPillarRoofNoise;
    @Shadow private NormalNoise icebergSurfaceNoise;
    @Shadow private NormalNoise icebergPillarNoise;
    @Shadow private NormalNoise icebergPillarRoofNoise;
    @Shadow private NormalNoise clayBandsOffsetNoise;

    @Unique
    private double farlands$real(NormalNoise noise, double x, double y, double z) {
        if (!FarProjection.isEpochActive()) {
            return noise.getValue(x, y, z);
        }
        double scale = farlands$scale(noise);
        return noise.getValue(x + FarProjection.epochBlockX() * scale, y,
            z + FarProjection.epochBlockZ() * scale);
    }

    @Unique
    private double farlands$scale(NormalNoise noise) {
        if (noise == this.badlandsPillarNoise) {
            return 0.2;
        }
        if (noise == this.badlandsPillarRoofNoise) {
            return 0.75;
        }
        if (noise == this.icebergPillarNoise) {
            return 1.28;
        }
        if (noise == this.icebergPillarRoofNoise) {
            return 1.17;
        }
        return 1.0;
    }

    @Redirect(method = "getSurfaceDepth",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$getSurfaceDepth(NormalNoise noise, double x, double y, double z) {
        return farlands$real(noise, x, y, z);
    }

    @Redirect(method = "getSurfaceSecondary",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$getSurfaceSecondary(NormalNoise noise, double x, double y, double z) {
        return farlands$real(noise, x, y, z);
    }

    @Redirect(method = "erodedBadlandsExtension",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$badlands(NormalNoise noise, double x, double y, double z) {
        return farlands$real(noise, x, y, z);
    }

    @Redirect(method = "frozenOceanExtension",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$iceberg(NormalNoise noise, double x, double y, double z) {
        return farlands$real(noise, x, y, z);
    }

    @Redirect(method = "getBand",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/synth/NormalNoise;getValue(DDD)D"))
    private double farlands$getBand(NormalNoise noise, double x, double y, double z) {
        return farlands$real(noise, x, y, z);
    }
}
