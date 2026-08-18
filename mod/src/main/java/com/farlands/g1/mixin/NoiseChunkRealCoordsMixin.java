package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Real-coordinate accessors for {@code NoiseChunk} (which doubles as the
 * noise pipeline's {@code FunctionContext}).
 *
 * <p>All unwrapping is delegated to {@link FarProjection}: on generation
 * threads the patched {@code forChunk} publishes the wide chunk origin and
 * the accessors rebuild continuous real coordinates (both half-axes); on a
 * stable client jar no origin is ever published and the unsigned fallback
 * applies, so one mod jar serves both client jar states.</p>
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkRealCoordsMixin {

    @Shadow private int cellStartBlockX;
    @Shadow private int cellStartBlockY;
    @Shadow private int cellStartBlockZ;
    @Shadow private int inCellX;
    @Shadow private int inCellY;
    @Shadow private int inCellZ;

    public double getBlockXDouble() {
        int v = cellStartBlockX + inCellX;
        if (FarProjection.isEpochActive()) {
            return FarProjection.realBlockX(v);
        }
        return FarProjection.unwrapX(v);
    }

    public double getBlockYDouble() {
        return (double) (cellStartBlockY + inCellY);
    }

    public double getBlockZDouble() {
        int v = cellStartBlockZ + inCellZ;
        if (FarProjection.isEpochActive()) {
            return FarProjection.realBlockZ(v);
        }
        return FarProjection.unwrapZ(v);
    }
}
