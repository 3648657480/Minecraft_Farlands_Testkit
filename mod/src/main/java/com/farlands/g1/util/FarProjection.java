package com.farlands.g1.util;

/**
 * The single projection for far coordinates: maps wrapped 32-bit coordinates
 * to their continuous far-domain values.
 *
 * <p>Convention (identical across the patcher and the mod):</p>
 * <ul>
 *   <li>{@link #blockReal(int)}: block coordinates in the negative extreme
 *       band are reinterpreted as unsigned 32-bit values, keeping the world
 *       continuous across the +/-2^31 wrap. Coordinates above
 *       {@link #WRAP_BAND} pass through unchanged (zero impact on normal
 *       play).</li>
 *   <li>{@link #chunkNorm(int)}: chunk coordinates below {@link #WRAP_BAND}
 *       are shifted by {@link #CHUNK_SHIFT} (2^28) so the negative extreme
 *       epoch maps onto the positive band used by chunk storage.</li>
 * </ul>
 */
public final class FarProjection {

    /** Below this value a coordinate is considered part of the wrapped band. */
    public static final int WRAP_BAND = -100_000_000;

    /** Chunk-coordinate shift applied to the negative extreme band (2^28). */
    public static final int CHUNK_SHIFT = 268_435_456;

    /** Generation origin for the current chunk (set by the NoiseChunk patch). */
    private static final ThreadLocal<Long> ORIGIN_X = new ThreadLocal<>();
    private static final ThreadLocal<Long> ORIGIN_Z = new ThreadLocal<>();

    /**
     * Freezes the coordinate domain at chunk construction: true when the
     * chunk's cells are epoch-relative. Chunks created before the epoch
     * engaged (spawn area at world load) keep the real domain even after
     * the epoch flips mid-generation.
     */
    private static final ThreadLocal<Boolean> GENERATION_EPOCH_CELLS = new ThreadLocal<>();

    public static void setGenerationEpochCells(boolean epochCells) {
        GENERATION_EPOCH_CELLS.set(epochCells);
    }

    /**
     * E line: the epoch origin in REAL block coordinates. Every int domain
     * (chunk, section, block, generation cell) is expressed relative to it,
     * so int math never overflows; real = epoch + local. Set once per world
     * join / teleport to the player's chunk. 0 = vanilla behavior.
     */
    private static volatile long epochBlockX;
    private static volatile long epochBlockZ;

    private FarProjection() {
    }

    public static void setEpoch(long epochX, long epochZ) {
        if (!epochSupported()) {
            return; // 非 epoch 客户端 jar（J1-J3）：epoch 必须保持休眠
        }
        epochBlockX = epochX;
        epochBlockZ = epochZ;
    }

    /**
     * The epoch is only ACTIVE when it is genuinely far away. The client
     * packet handler re-centers the epoch on every chunk-cache-center
     * packet, including the spawn (tiny, e.g. 128 blocks) - treating that
     * as active shifted the whole entity domain at the spawn (AABB
     * explosion, chunk chaos). Tiny epochs are semantically vanilla and
     * must stay dormant.
     */
    public static boolean isEpochActive() {
        return Math.abs(epochBlockX) > 100_000_000L || Math.abs(epochBlockZ) > 100_000_000L;
    }

    public static long epochBlockX() {
        return epochBlockX;
    }

    public static long epochBlockZ() {
        return epochBlockZ;
    }

    /** Epoch chunk delta: real chunk = local + this. */
    public static int epochChunkDeltaX() {
        return (int) (epochBlockX >> 4);
    }

    /** Epoch chunk delta: real chunk = local + this. */
    public static int epochChunkDeltaZ() {
        return (int) (epochBlockZ >> 4);
    }

    /** Real chunk coordinate of an epoch-relative chunk coordinate; gated
     *  so real-domain callers (structure references, spawn finder) pass
     *  through untranslated. */
    public static int epochRealChunkX(int local) {
        if (isEpochActive() && Math.abs(local) < 1_000_000) {
            return (int) (epochBlockX >> 4) + local;
        }
        return local;
    }

    /** Real chunk coordinate of an epoch-relative chunk coordinate; gated
     *  so real-domain callers (structure references, spawn finder) pass
     *  through untranslated. */
    public static int epochRealChunkZ(int local) {
        if (isEpochActive() && Math.abs(local) < 1_000_000) {
            return (int) (epochBlockZ >> 4) + local;
        }
        return local;
    }

    /**
     * Center-based translation for the WorldGenRegion boundary: translate
     * the request by +-the epoch delta, whichever moves it closer to the
     * generating chunk's real position. Local generation queries stay put;
     * real-domain queries (carvers, structure lookahead, world-spawn-area
     * remnants) get rebased onto the region's local domain. Ties prefer
     * the identity.
     */
    public static int epochTranslatedChunkX(int x, int centerX) {
        if (!isEpochActive()) return x;
        long delta = epochChunkDeltaX();
        long dId = Math.abs((long) x - centerX);
        long dPlus = Math.abs((long) x + delta - centerX);
        long dMinus = Math.abs((long) x - delta - centerX);
        if (dPlus < dId && dPlus <= dMinus) {
            return (int) (x + delta);
        }
        if (dMinus < dId) {
            return (int) (x - delta);
        }
        return x;
    }

    /** Center-based translation for the WorldGenRegion boundary (Z axis). */
    public static int epochTranslatedChunkZ(int z, int centerZ) {
        if (!isEpochActive()) return z;
        long delta = epochChunkDeltaZ();
        long dId = Math.abs((long) z - centerZ);
        long dPlus = Math.abs((long) z + delta - centerZ);
        long dMinus = Math.abs((long) z - delta - centerZ);
        if (dPlus < dId && dPlus <= dMinus) {
            return (int) (z + delta);
        }
        if (dMinus < dId) {
            return (int) (z - delta);
        }
        return z;
    }

    private static volatile int epochSupported = -1;

    /** Whether the client jar carries the E-line epoch patches. */
    public static boolean epochSupported() {
        int s = epochSupported;
        if (s == -1) {
            try {
                net.minecraft.world.level.ChunkPos.class.getDeclaredField("farlands$epoch");
                s = 1;
            } catch (ReflectiveOperationException e) {
                s = 0;
            }
            epochSupported = s;
        }
        return s == 1;
    }

    /** Epoch-relative min block X of a real chunk coordinate. */
    public static int epochMinBlockX(long realChunk) {
        if (!isEpochActive()) {
            return (int) (realChunk << 4);
        }
        return (int) ((realChunk - (epochBlockX >> 4)) << 4);
    }

    /** Epoch-relative min block Z of a real chunk coordinate. */
    public static int epochMinBlockZ(long realChunk) {
        if (!isEpochActive()) {
            return (int) (realChunk << 4);
        }
        return (int) ((realChunk - (epochBlockZ >> 4)) << 4);
    }

    /** Real block coordinate of an epoch-relative block value. */
    public static double realBlockX(int local) {
        return (double) epochBlockX + (double) local;
    }

    /** Real block coordinate of an epoch-relative block value. */
    public static double realBlockZ(int local) {
        return (double) epochBlockZ + (double) local;
    }

    /** Epoch-relative chunk coordinate of a real chunk coordinate. */
    public static int epochChunkX(long realChunk) {
        if (!isEpochActive()) {
            return (int) realChunk;
        }
        return (int) (realChunk - (epochBlockX >> 4));
    }

    /** Epoch-relative chunk coordinate of a real chunk coordinate. */
    public static int epochChunkZ(long realChunk) {
        if (!isEpochActive()) {
            return (int) realChunk;
        }
        return (int) (realChunk - (epochBlockZ >> 4));
    }

    /** Real chunk coordinate of an epoch-relative chunk coordinate. */
    public static long realChunkX(int local) {
        return (epochBlockX >> 4) + (long) local;
    }

    /** Called by the patched NoiseChunk.forChunk before generation starts. */
    public static void setGenerationOrigin(long originX, long originZ) {
        ORIGIN_X.set(originX);
        ORIGIN_Z.set(originZ);
    }

    /**
     * Origin-aware unwrap: when a generation origin is set, interpret the
     * wrapped value as the coordinate nearest to the origin (preserving the
     * sign of both half-axes); otherwise return the signed value unchanged.
     *
     * <p>The unsigned fallback was removed at the 2^31 milestone: negative
     * coordinates are valid ints there, and the unsigned reinterpretation
     * mirrored the negative half-axis onto the positive one (rendering,
     * collision). Unsigned handling belongs to the E line (beyond 2^31).</p>
     */
    public static double unwrapX(int v) {
        Boolean epochCells = GENERATION_EPOCH_CELLS.get();
        if (epochCells != null && epochCells && isEpochActive()) {
            return (double) epochBlockX + (double) v;
        }
        Long origin = ORIGIN_X.get();
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return (double) (origin + (v - (int) (long) origin));
        }
        if (isEpochActive()) {
            return (double) epochBlockX + (double) v;
        }
        return (double) v;
    }

    public static double unwrapZ(int v) {
        Boolean epochCells = GENERATION_EPOCH_CELLS.get();
        if (epochCells != null && epochCells && isEpochActive()) {
            return (double) epochBlockZ + (double) v;
        }
        Long origin = ORIGIN_Z.get();
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return (double) (origin + (v - (int) (long) origin));
        }
        if (isEpochActive()) {
            return (double) epochBlockZ + (double) v;
        }
        return (double) v;
    }

    /** Real (signed, continuous) block coordinate as a double. */
    public static double blockReal(int block) {
        return (double) block;
    }

    /** Real block coordinate of a long-domain value. */
    public static double blockReal(long block) {
        return block < WRAP_BAND ? (double) (block + (1L << 32)) : (double) block;
    }

    /**
     * Epoch-normalized chunk coordinate.
     *
     * <p>Identity at the 2^31 milestone: the negative chunk band is entirely
     * valid int territory, and any shift would alias it onto real positive
     * chunks past the seam (observed: negative side rendered the positive
     * side's chunks). Epoch shifting becomes necessary only beyond 2^31
     * (E line).</p>
     */
    public static int chunkNorm(int chunk) {
        return chunk;
    }

    /** Whether the coordinate lies in the wrapped band. */
    public static boolean isWrapped(int v) {
        return v < WRAP_BAND;
    }
}
