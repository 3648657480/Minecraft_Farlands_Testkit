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
     *
     * <p>Stored as double so the extreme target (up to 1e306) is
     * representable; the engine int domains only ever see the local part.</p>
     */
    private static volatile double epochBlockX;
    private static volatile double epochBlockZ;
    /** Exact epoch origin (BigInteger) - survives beyond double's range. */
    private static volatile java.math.BigInteger epochBigX = java.math.BigInteger.ZERO;
    private static volatile java.math.BigInteger epochBigZ = java.math.BigInteger.ZERO;
    /** True once an epoch has been set (even 0) - the epoch machinery is live. */
    private static volatile boolean epochInitialized = false;

    private FarProjection() {
    }

    public static void setEpoch(long epochX, long epochZ) {
        if (!epochSupported()) {
            return; // 闈?epoch 瀹㈡埛绔?jar锛圝1-J3锛夛細epoch 蹇呴』淇濇寔浼戠湢
        }
        setEpoch(java.math.BigInteger.valueOf(epochX), java.math.BigInteger.valueOf(epochZ));
    }

    public static void setEpoch(double epochX, double epochZ) {
        if (!epochSupported()) {
            return;
        }
        setEpoch(java.math.BigDecimal.valueOf(epochX).toBigInteger(),
            java.math.BigDecimal.valueOf(epochZ).toBigInteger());
    }

    /** Exact epoch setter (BigInteger). */
    public static void setEpoch(java.math.BigInteger x, java.math.BigInteger z) {
        if (!epochSupported()) {
            return;
        }
        epochBigX = x;
        epochBigZ = z;
        epochBlockX = x.doubleValue();
        epochBlockZ = z.doubleValue();
        epochInitialized = true;
    }

    /**
     * Clears the epoch state (called when a world unloads/loads) so a
     * previous world's epoch never leaks into a freshly created one.
     */
    public static void resetEpoch() {
        epochBigX = java.math.BigInteger.ZERO;
        epochBigZ = java.math.BigInteger.ZERO;
        epochBlockX = 0.0;
        epochBlockZ = 0.0;
        epochInitialized = false;
    }

    /** Exact epoch origin (BigInteger). */
    public static java.math.BigInteger epochBigX() {
        return epochBigX;
    }

    /** Exact epoch origin (BigInteger). */
    public static java.math.BigInteger epochBigZ() {
        return epochBigZ;
    }

    /**
     * Seed offset for integer-domain carvers/features under the current epoch:
     * 0 at the origin (so vanilla behavior is bit-identical) and a deterministic
     * mix of the epoch chunk coordinates otherwise, so far-domain carvers and
     * features do not repeat with the local int window.
     */
    public static long epochSeedOffset() {
        if (!isEpochActive()) {
            return 0L;
        }
        java.math.BigInteger bx = epochBigX();
        java.math.BigInteger bz = epochBigZ();
        if (bx == null || bz == null) {
            return 0L;
        }
        long cx = bx.shiftRight(4).longValue();
        long cz = bz.shiftRight(4).longValue();
        return cx * 341873128712L + cz * 132897987541L;
    }

    /** Exact real coordinate of a local block value. */
    public static java.math.BigInteger realBlockBigX(long local) {
        return epochBigX.add(java.math.BigInteger.valueOf(local));
    }

    /** Exact real coordinate of a local block value. */
    public static java.math.BigInteger realBlockBigZ(long local) {
        return epochBigZ.add(java.math.BigInteger.valueOf(local));
    }

    /**
     * The epoch is ACTIVE whenever it has been set (the fixed-epoch design:
     * set once from the world's respawn point at load, constant for the
     * session). Every int domain runs local when the jar supports the epoch;
     * on jars without the epoch patches the epoch stays 0 and everything is
     * vanilla.
     */
    public static boolean isEpochActive() {
        return epochInitialized;
    }

    public static double epochBlockX() {
        return epochBlockX;
    }

    public static double epochBlockZ() {
        return epochBlockZ;
    }

    /** Epoch chunk delta: real chunk = local + this. */
    public static int epochChunkDeltaX() {
        return (int) (epochBlockX / 16.0);
    }

    /** Epoch chunk delta: real chunk = local + this. */
    public static int epochChunkDeltaZ() {
        return (int) (epochBlockZ / 16.0);
    }

    /** Real chunk coordinate of an epoch-relative chunk coordinate; gated
     *  so real-domain callers (structure references, spawn finder) pass
     *  through untranslated. */
    public static int epochRealChunkX(int local) {
        if (isEpochActive() && Math.abs(local) < 1_000_000) {
            return (int) (epochBlockX / 16.0) + local;
        }
        return local;
    }

    /** Real chunk coordinate of an epoch-relative chunk coordinate; gated
     *  so real-domain callers (structure references, spawn finder) pass
     *  through untranslated. */
    public static int epochRealChunkZ(int local) {
        if (isEpochActive() && Math.abs(local) < 1_000_000) {
            return (int) (epochBlockZ / 16.0) + local;
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
        return (int) (((double) (realChunk << 4)) - epochBlockX);
    }

    /** Epoch-relative min block Z of a real chunk coordinate. */
    public static int epochMinBlockZ(long realChunk) {
        if (!isEpochActive()) {
            return (int) (realChunk << 4);
        }
        return (int) (((double) (realChunk << 4)) - epochBlockZ);
    }

    /** Real block coordinate of an epoch-relative block value. */
    public static double realBlockX(int local) {
        return epochBlockX + (double) local;
    }

    /** Real block coordinate of an epoch-relative block value. */
    public static double realBlockZ(int local) {
        return epochBlockZ + (double) local;
    }

    /** Epoch-relative chunk coordinate of a real chunk coordinate. */
    public static int epochChunkX(long realChunk) {
        if (!isEpochActive()) {
            return (int) realChunk;
        }
        return (int) ((double) realChunk - (epochBlockX / 16.0));
    }

    /** Epoch-relative chunk coordinate of a real chunk coordinate. */
    public static int epochChunkZ(long realChunk) {
        if (!isEpochActive()) {
            return (int) realChunk;
        }
        return (int) ((double) realChunk - (epochBlockZ / 16.0));
    }

    /** Real chunk coordinate of an epoch-relative chunk coordinate. */
    public static long realChunkX(int local) {
        return (long) (epochBlockX / 16.0) + (long) local;
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
            return applySamplePolicy(epochBlockX + (double) v, 0);
        }
        Long origin = ORIGIN_X.get();
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return applySamplePolicy((double) (origin + (v - (int) (long) origin)), 0);
        }
        if (isEpochActive()) {
            return applySamplePolicy(epochBlockX + (double) v, 0);
        }
        return (double) v;
    }

    public static double unwrapZ(int v) {
        Boolean epochCells = GENERATION_EPOCH_CELLS.get();
        if (epochCells != null && epochCells && isEpochActive()) {
            return applySamplePolicy((double) epochBlockZ + (double) v, 1);
        }
        Long origin = ORIGIN_Z.get();
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return applySamplePolicy((double) (origin + (v - (int) (long) origin)), 1);
        }
        if (isEpochActive()) {
            return applySamplePolicy((double) epochBlockZ + (double) v, 1);
        }
        return (double) v;
    }

    /**
     * Worldgen sampling policy (EXPERIMENTAL, user-configurable) plus the
     * pro experimental transform.
     *
     * <p>The pro transform runs first and defaults to a strict no-op:</p>
     * <ul>
     *   <li>{@code pro_sample_scale} multiplies the horizontal coordinate</li>
     *   <li>{@code pro_sample_offset_x/z} is added afterwards</li>
     * </ul>
     *
     * <p>Beyond {@code worldgen_far_threshold} (default 2^53) the real sample
     * coordinate is transformed according to {@code worldgen_sample_mode}:</p>
     * <ul>
     *   <li>{@code raw} - untouched (native phenomena; default)</li>
     *   <li>{@code clamp} - clamped to +/-{@code worldgen_sample_clamp}
     *       (finite but repeated terrain; avoids Infinity)</li>
     *   <li>{@code quantize} - snapped to the 2^53 grid (flat/stable)</li>
     * </ul>
     * <p>Bad parameters are the user's own risk; see docs/USAGE.md.</p>
     *
     * @param axis 0 = X, 1 = Z (pro offsets differ per axis)
     */
    private static double applySamplePolicy(double real, int axis) {
        if (!isEpochActive()) {
            return real;
        }
        // pro experimental transform (defaults are strict no-ops)
        double scale = com.farlands.g1.util.FarConfig.proSampleScale();
        if (scale != 1.0) {
            real *= scale;
        }
        double proOff = axis == 1
            ? com.farlands.g1.util.FarConfig.proSampleOffsetZ()
            : com.farlands.g1.util.FarConfig.proSampleOffsetX();
        if (proOff != 0.0) {
            real += proOff;
        }
        long threshold = com.farlands.g1.util.FarConfig.worldgenFarThreshold();
        if (threshold > 0 && Math.abs(real) < (double) threshold) {
            return real;
        }
        String mode = com.farlands.g1.util.FarConfig.worldgenSampleMode();
        double out;
        if ("clamp".equals(mode)) {
            double c = com.farlands.g1.util.FarConfig.worldgenSampleClamp();
            out = Math.max(-c, Math.min(c, real));
        } else if ("quantize".equals(mode)) {
            double grid = 9007199254740992.0; // 2^53
            out = Math.rint(real / grid) * grid;
        } else {
            out = real;
        }
        if (com.farlands.g1.util.FarConfig.debug() >= 3 && farlands$allowSampleLog()) {
            System.out.println("[FarLands-G1] sample axis=" + (axis == 1 ? "Z" : "X")
                + " mode=" + mode + " real=" + real + " out=" + out);
        }
        return out;
    }

    // debug>=3 per-sample log: hard-capped so a stray debug=3 can never
    // explode the log or stall the world (it used to log every sample).
    private static final java.util.concurrent.atomic.AtomicInteger farlands$sampleLogs =
        new java.util.concurrent.atomic.AtomicInteger();
    private static final int farlands$SAMPLE_LOG_CAP = 2000;
    private static volatile boolean farlands$sampleLogCapped = false;

    private static boolean farlands$allowSampleLog() {
        if (farlands$sampleLogCapped) {
            return false;
        }
        if (farlands$sampleLogs.incrementAndGet() <= farlands$SAMPLE_LOG_CAP) {
            return true;
        }
        farlands$sampleLogCapped = true;
        System.out.println("[FarLands-G1] per-sample log capped at "
            + farlands$SAMPLE_LOG_CAP + " lines; further samples suppressed");
        System.out.flush();
        return false;
    }

    /** Real (signed, continuous) block coordinate as a double. */
    public static double blockReal(int block) {
        return (double) block;
    }

    /**
     * Collision-domain X for the patched collision iterators (AABB.clip,
     * BlockCollisions). The world is entirely local when the epoch is
     * active, so collision boxes must use the local value directly - the
     * real-coordinate unwrap (epoch + local) would move block boxes to the
     * epoch origin and kill all collisions. Without an epoch this keeps
     * the legacy unsigned fallback used by the J3/D-line patches.
     */
    public static double collisionX(int v) {
        if (isEpochActive()) {
            return (double) v;
        }
        if (v < -100_000_000) {
            return (double) Integer.toUnsignedLong(v);
        }
        return (double) v;
    }

    /** Collision-domain Z; see {@link #collisionX(int)}. */
    public static double collisionZ(int v) {
        if (isEpochActive()) {
            return (double) v;
        }
        if (v < -100_000_000) {
            return (double) Integer.toUnsignedLong(v);
        }
        return (double) v;
    }

    /** F3 display: real X of a local double (epoch + local). */
    public static double displayX(double local) {
        return epochBlockX + local;
    }

    /** F3 display: real Z of a local double (epoch + local). */
    public static double displayZ(double local) {
        return epochBlockZ + local;
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
