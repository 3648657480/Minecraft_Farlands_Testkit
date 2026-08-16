package com.farlands.g1.runtime;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * Positional random factory entry with unsigned coordinate normalization.
 * The aquifer seeds its random cells from int grid coordinates; converting
 * the wrapped negative band to the positive unsigned domain keeps the seeds
 * continuous across +/-2^31.
 */
public final class FarAquiferHelper {

    private FarAquiferHelper() {
    }

    public static RandomSource at(PositionalRandomFactory factory, int x, int y, int z) {
        if (x < -100_000_000) {
            x = (int) ((long) x + 4294967296L);
        }
        if (z < -100_000_000) {
            z = (int) ((long) z + 4294967296L);
        }
        return factory.at(x, y, z);
    }
}
