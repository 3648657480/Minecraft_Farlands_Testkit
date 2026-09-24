package com.farlands.g1.runtime;

import com.farlands.g1.util.FarProjection;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * B line: positional-random entry that folds the epoch into the position, for
 * integer-coordinate consumers that seed an RNG from the LOCAL block coords
 * (ore veins, surface vertical gradient, ...). At the origin the offset is 0,
 * so behavior is bit-identical to vanilla.
 */
public final class FarRandom {

    private FarRandom() {
    }

    public static RandomSource at(PositionalRandomFactory factory, int x, int y, int z) {
        if (!FarProjection.isEpochActive()) {
            return factory.at(x, y, z);
        }
        long off = FarProjection.epochSeedOffset();
        return factory.at(x + (int) off, y, z + (int) (off >>> 32));
    }
}
