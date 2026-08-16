package com.farlands.g1.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DensityFunction.FunctionContext.class)
public interface FunctionContextMixin {
    default double getBlockXDouble() { return ((DensityFunction.FunctionContext) this).blockX(); }
    default double getBlockYDouble() { return ((DensityFunction.FunctionContext) this).blockY(); }
    default double getBlockZDouble() { return ((DensityFunction.FunctionContext) this).blockZ(); }
}
