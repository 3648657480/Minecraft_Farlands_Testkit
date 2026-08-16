package com.farlands.g1.util;

public final class FarLandsEpoch {
    public static volatile long centerX;
    public static volatile long centerZ;
    public static volatile boolean ready;

    public static int wrap() {
        try {
            java.lang.reflect.Field f = net.minecraft.core.SectionPos.class.getDeclaredField("farlands$wrap");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            return 4194304; // 2^22, the vanilla SectionPos key layout
        }
    }

    public static int correctX(int raw) {
        if (!ready) return raw;
        return correct(raw, centerX);
    }

    public static int correctZ(int raw) {
        if (!ready) return raw;
        return correct(raw, centerZ);
    }

    private static int correct(int raw, long center) {
        int wrap = wrap();
        long diff = center - (long)raw;
        long wraps = Math.round((double)diff / wrap);
        long corrected = (long)raw + wraps * wrap;
        if (corrected > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (corrected < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int)corrected;
    }
}
