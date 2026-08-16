package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(WorldBorder.class)
public class WorldBorderMixin {
    @Overwrite
    public boolean isWithinBounds(BlockPos pos) {
        return true;
    }

    @Overwrite
    public boolean isWithinBounds(double x, double z) {
        return true;
    }

    @Overwrite
    public boolean isWithinBounds(double x, double z, double margin) {
        return true;
    }

    @Overwrite
    public double getSize() {
        return Double.MAX_VALUE;
    }

    @Overwrite
    public int getAbsoluteMaxSize() {
        return Integer.MAX_VALUE;
    }
}
