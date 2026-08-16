package com.farlands.g1.runtime;

import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * FunctionContext whose double accessors reinterpret wrapped negative
 * coordinates as unsigned, keeping noise sampling continuous across the
 * +/-2^31 wrap. The int accessors preserve the wrapped view for int-domain
 * consumers (cache indexing etc.).
 */
public final class RealContext implements DensityFunction.FunctionContext {

    private final int x;
    private final int y;
    private final int z;

    public RealContext(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public int blockX() {
        return x;
    }

    @Override
    public int blockY() {
        return y;
    }

    @Override
    public int blockZ() {
        return z;
    }

    public double getBlockXDouble() {
        return com.farlands.g1.util.FarProjection.unwrapX(x);
    }

    public double getBlockYDouble() {
        return (double) y;
    }

    public double getBlockZDouble() {
        return com.farlands.g1.util.FarProjection.unwrapZ(z);
    }
}
