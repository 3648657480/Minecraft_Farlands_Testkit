package com.farlands.g1.util;

import net.minecraft.world.phys.Vec3;

public final class FloatingOrigin {
    private static volatile Vec3 origin = Vec3.ZERO;

    public static void updateRaw(Vec3 o) { origin = o; }
    public static Vec3 get() { return origin; }
    public static boolean isActive() { return origin.x != 0.0 || origin.y != 0.0 || origin.z != 0.0; }
}
