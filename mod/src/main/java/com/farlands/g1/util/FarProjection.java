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

    private FarProjection() {
    }

    /** Called by the patched NoiseChunk.forChunk before generation starts. */
    public static void setGenerationOrigin(long originX, long originZ) {
        ORIGIN_X.set(originX);
        ORIGIN_Z.set(originZ);
    }

    /**
     * Origin-aware unwrap: when a generation origin is set, interpret the
     * wrapped value as the coordinate nearest to the origin (preserving the
     * sign of both half-axes); otherwise fall back to the unsigned view.
     */
    public static double unwrapX(int v) {
        Long origin = ORIGIN_X.get();
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return (double) (origin + (v - (int) (long) origin));
        }
        return v < WRAP_BAND ? (double) Integer.toUnsignedLong(v) : (double) v;
    }

    public static double unwrapZ(int v) {
        Long origin = ORIGIN_Z.get();
        if (origin != null && (origin < -100_000_000L || origin > 100_000_000L)) {
            return (double) (origin + (v - (int) (long) origin));
        }
        return v < WRAP_BAND ? (double) Integer.toUnsignedLong(v) : (double) v;
    }

    /** Real (unsigned, continuous) block coordinate as a double. */
    public static double blockReal(int block) {
        return block < WRAP_BAND ? (double) Integer.toUnsignedLong(block) : (double) block;
    }

    /** Real block coordinate of a long-domain value. */
    public static double blockReal(long block) {
        return block < WRAP_BAND ? (double) (block + (1L << 32)) : (double) block;
    }

    /** Epoch-normalized chunk coordinate. */
    public static int chunkNorm(int chunk) {
        return chunk < WRAP_BAND ? chunk + CHUNK_SHIFT : chunk;
    }

    /** Whether the coordinate lies in the wrapped band. */
    public static boolean isWrapped(int v) {
        return v < WRAP_BAND;
    }
}
