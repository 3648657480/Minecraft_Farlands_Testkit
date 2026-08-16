package com.farlands.g1.mixin;

import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Vec3.class)
public class Vec3RealCoordsMixin {

    @Unique
    private static double farlands$real(int v) {
        return com.farlands.g1.util.FarProjection.unwrapX(v);
    }

    @Overwrite
    public static Vec3 atLowerCornerOf(final Vec3i pos) {
        return new Vec3(farlands$real(pos.getX()), (double)pos.getY(), farlands$real(pos.getZ()));
    }

    @Overwrite
    public static Vec3 atLowerCornerWithOffset(final Vec3i pos, final double ox, final double oy, final double oz) {
        return new Vec3(farlands$real(pos.getX()) + ox, (double)pos.getY() + oy, farlands$real(pos.getZ()) + oz);
    }
}
