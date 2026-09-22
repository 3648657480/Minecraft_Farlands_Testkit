package com.farlands.harness;

import net.minecraft.world.level.levelgen.DensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * F0-2 bisection shim: the patched call sites in {@code DensityFunctions$Noise}
 * and {@code $ShiftedNoise} invoke {@code getBlockX/Y/ZDouble()}, which the
 * FarLands mod normally provides. This interface mixin supplies the exact
 * same vanilla-equivalent defaults (plain int cast) so the patched reference
 * jar can run without the FarLands mod. It carries no far-coordinate logic.
 */
@Mixin(DensityFunction.FunctionContext.class)
public interface FunctionContextShim {

    @Unique
    default double getBlockXDouble() {
        return (double) ((DensityFunction.FunctionContext) this).blockX();
    }

    @Unique
    default double getBlockYDouble() {
        return (double) ((DensityFunction.FunctionContext) this).blockY();
    }

    @Unique
    default double getBlockZDouble() {
        return (double) ((DensityFunction.FunctionContext) this).blockZ();
    }
}
