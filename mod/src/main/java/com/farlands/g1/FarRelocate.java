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
        public final int dx;
        public final int dz;

        public Request(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }
    }

    /** Written on the server thread (tickServer), consumed on the client thread (runTick). */
    public static volatile Request pending;

    private FarRelocate() {
    }
}
