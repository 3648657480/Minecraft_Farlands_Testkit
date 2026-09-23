package com.farlands.g1.runtime;

/**
 * Mod-side view of {@link net.minecraft.world.level.levelgen.DensityFunction.FunctionContext}
 * exposing the real (unwrapped) block coordinates as doubles. Added to the
 * interface by {@code FunctionContextMixin}; {@code NoiseChunk} overrides them
 * with the wide real coordinates. Ordinary mod code casts a context to this
 * interface to read the real coordinate (the mixin-injected accessor is not
 * visible to the Java compiler otherwise).
 */
public interface RealCoords {
    double getBlockXDouble();

    double getBlockYDouble();

    double getBlockZDouble();
}
