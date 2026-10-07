package com.farlands.g1;

import java.math.BigInteger;

/**
 * E3: shared state for the auto-relocate flow.
 *
 * <p>The server thread detects the player approaching the int chunk
 * boundary and sets {@link #pending}; the client thread consumes it in
 * {@code Minecraft.runTick} and runs halt -> translate -> openWorld.</p>
 */
public final class FarRelocate {

    public static final class Request {
        public final long dx;
        public final long dz;
        /** Exact new epoch (BigInteger; null = keep current). */
        public final BigInteger newEpochBigX;
        public final BigInteger newEpochBigZ;
        /**
         * true = archive the current epoch's chunks and restore the target
         * epoch's archive (if any) - used by /realtp; false = shift the save
         * in place (used by the walking auto-relocate so the terrain around
         * the player follows).
         */
        public final boolean archive;

        public Request(long dx, long dz) {
            this(dx, dz, null, null, false);
        }

        public Request(long dx, long dz, BigInteger newEpochBigX, BigInteger newEpochBigZ) {
            this(dx, dz, newEpochBigX, newEpochBigZ, false);
        }

        public Request(long dx, long dz, BigInteger newEpochBigX, BigInteger newEpochBigZ, boolean archive) {
            this.dx = dx;
            this.dz = dz;
            this.newEpochBigX = newEpochBigX;
            this.newEpochBigZ = newEpochBigZ;
            this.archive = archive;
        }
    }

    /** Written on the server thread (tickServer), consumed on the client thread (runTick). */
    public static volatile Request pending;

    /**
     * Exact local arrival position after a /realtp relocation (real target
     * minus the NEW epoch). Consumed by PlayerListMixin on the next join so
     * the first /realtp lands on the exact requested point; null = fall back
     * to the local-spawn reset.
     */
    public static volatile double[] pendingLocalTarget;

    private FarRelocate() {
    }
}
