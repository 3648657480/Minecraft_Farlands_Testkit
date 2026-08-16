package com.farlands.g1.mixin;

import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Mth.class)
public class MthFloorMixin {
    @Overwrite
    public static int floor(double v) {
        return (int)(long)Math.floor(v);
    }

    @Overwrite
    public static int floor(float v) {
        return (int)(long)Math.floor((double)v);
    }
}
