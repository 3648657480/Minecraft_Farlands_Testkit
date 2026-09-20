package com.farlands.g1;

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
        /** New epoch to persist after the shift (NaN = keep current). */
        public final double newEpochX;
        public final double newEpochZ;
        /**
         * true = archive the current epoch's chunks and restore the target
         * epoch's archive (if any) - used by /realtp; false = shift the save
         * in place (used by the walking auto-relocate so the terrain around
         * the player follows).
         */
        public final boolean archive;

        public Request(long dx, long dz) {
            this(dx, dz, Double.NaN, Double.NaN, false);
        }

        public Request(long dx, long dz, double newEpochX, double newEpochZ) {
            this(dx, dz, newEpochX, newEpochZ, false);
        }

        public Request(long dx, long dz, double newEpochX, double newEpochZ, boolean archive) {
            this.dx = dx;
            this.dz = dz;
            this.newEpochX = newEpochX;
            this.newEpochZ = newEpochZ;
            this.archive = archive;
        }
    }

    /** Written on the server thread (tickServer), consumed on the client thread (runTick). */
    public static volatile Request pending;

    private FarRelocate() {
    }
}
