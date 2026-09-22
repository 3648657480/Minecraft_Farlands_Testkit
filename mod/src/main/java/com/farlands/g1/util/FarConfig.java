package com.farlands.g1.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * E line: per-world configuration ({@code farlands.properties} in the world
 * directory). JVM properties override the file, which overrides defaults.
 *
 * <p>See {@link #save()} for the annotated file layout.</p>
 */
public final class FarConfig {

    // ---- epoch ----
    private static volatile java.math.BigInteger epochBigX = null;
    private static volatile java.math.BigInteger epochBigZ = null;
    // ---- relocation ----
    private static volatile boolean autoRelocate = true;
    private static volatile double relocateMargin = 100_000.0;
    private static volatile long relocateDiscardOver = Integer.MAX_VALUE;
    // ---- performance ----
    private static volatile int fluidTickLimit = 2000;
    // ---- storage ----
    private static volatile String archiveDir = "farlands_epochs";
    // ---- debug ----
    private static volatile boolean debug = false;

    private static volatile Path file;

    private FarConfig() {
    }

    /** Loads the config for a world; call before the epoch is applied. */
    public static void load(Path worldDir) {
        // reset cross-world static state first: a previous world's epoch must
        // never leak into a freshly created one
        epochBigX = null;
        epochBigZ = null;
        autoRelocate = true;
        relocateMargin = 100_000.0;
        relocateDiscardOver = Integer.MAX_VALUE;
        fluidTickLimit = 2000;
        archiveDir = "farlands_epochs";
        debug = false;
        FarProjection.resetEpoch();
        file = worldDir.resolve("farlands.properties");
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
            } catch (Exception e) {
                System.out.println("[FarLands-G1] config read FAILED: " + e);
            }
        }
        // migrate the legacy epoch file
        Path legacy = worldDir.resolve("farlands_epoch.txt");
        if (!p.containsKey("epoch_x") && Files.isRegularFile(legacy)) {
            try {
                String[] parts = Files.readString(legacy).trim().split(",");
                p.setProperty("epoch_x", parts[0].trim());
                p.setProperty("epoch_z", parts[1].trim());
            } catch (Exception ignored) {
            }
        }
        // JVM overrides
        String spawnset = System.getProperty("farlands.spawnset");
        if (spawnset != null && !spawnset.isEmpty()) {
            String[] parts = spawnset.split(",");
            p.setProperty("epoch_x", parts[0].trim());
            p.setProperty("epoch_z", parts[2].trim());
        }
        override(p, "farlands.auto_relocate", "auto_relocate");
        override(p, "farlands.relocate_margin", "relocate_margin");
        override(p, "farlands.relocate_discard_over", "relocate_discard_over");
        override(p, "farlands.fluid_tick_limit", "fluid_tick_limit");
        override(p, "farlands.archive_dir", "archive_dir");
        override(p, "farlands.debug", "debug");

        try {
            if (p.containsKey("epoch_x")) {
                epochBigX = new java.math.BigDecimal(p.getProperty("epoch_x").trim()).toBigInteger();
                epochBigZ = new java.math.BigDecimal(p.getProperty("epoch_z", "0").trim()).toBigInteger();
            }
            autoRelocate = Boolean.parseBoolean(p.getProperty("auto_relocate", "true"));
            relocateMargin = Double.parseDouble(p.getProperty("relocate_margin", "100000"));
            relocateDiscardOver = Long.parseLong(p.getProperty("relocate_discard_over", "2147483647"));
            fluidTickLimit = Integer.parseInt(p.getProperty("fluid_tick_limit", "2000"));
            archiveDir = p.getProperty("archive_dir", "farlands_epochs").trim();
            debug = Boolean.parseBoolean(p.getProperty("debug", "false"));
        } catch (NumberFormatException e) {
            System.out.println("[FarLands-G1] config parse FAILED: " + e);
        }
        if (epochBigX == null) {
            // auto-create: a fresh world gets an epoch at the origin so the
            // epoch machinery (and /realtp) is live from the start
            epochBigX = java.math.BigInteger.ZERO;
            epochBigZ = java.math.BigInteger.ZERO;
            System.out.println("[FarLands-G1] config auto-created (epoch = origin)");
        }
        if (spawnset != null || !Files.isRegularFile(file)) {
            save();
        }
    }

    private static void override(Properties p, String jvmKey, String fileKey) {
        String v = System.getProperty(jvmKey);
        if (v != null && !v.isEmpty()) {
            p.setProperty(fileKey, v);
        }
    }

    /** Persists the current values (annotated, grouped, human-readable). */
    public static void save() {
        Path f = file;
        if (f == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ============================================================\n");
            sb.append("#  FarLands G1 - world configuration\n");
            sb.append("#  Edit values, then re-enter the world (some need a restart).\n");
            sb.append("#  JVM flags override this file: -Dfarlands.<key>=<value>\n");
            sb.append("# ============================================================\n");
            sb.append('\n');
            sb.append("# ---- epoch: the world origin (real coordinate, exact) ----\n");
            sb.append("# (0,0) = world origin. Changing this shifts all generated\n");
            sb.append("# chunks - prefer /realtp or a fresh world.\n");
            if (epochBigX != null) {
                sb.append("epoch_x=").append(epochBigX).append('\n');
                sb.append("epoch_z=").append(epochBigZ).append('\n');
            }
            sb.append('\n');
            sb.append("# ---- relocation: what happens near the window edge ----\n");
            sb.append("# true  = walking near the edge re-centers automatically\n");
            sb.append("# false = only warn; continue manually with /realtp\n");
            sb.append("auto_relocate=").append(autoRelocate).append('\n');
            sb.append("# trigger distance from the edge (blocks, >= 100000)\n");
            sb.append("relocate_margin=").append((long) relocateMargin).append('\n');
            sb.append("# shifts larger than this (chunks) switch to archive mode\n");
            sb.append("relocate_discard_over=").append(relocateDiscardOver).append('\n');
            sb.append('\n');
            sb.append("# ---- performance ----\n");
            sb.append("# max fluid ticks per game tick (0 = unlimited; 2000 is safe\n");
            sb.append("# for anomalous terrain)\n");
            sb.append("fluid_tick_limit=").append(fluidTickLimit).append('\n');
            sb.append('\n');
            sb.append("# ---- storage ----\n");
            sb.append("# per-epoch chunk archive directory (relative to the world)\n");
            sb.append("archive_dir=").append(archiveDir).append('\n');
            sb.append('\n');
            sb.append("# ---- debug ----\n");
            sb.append("# true = verbose runtime logging\n");
            sb.append("debug=").append(debug).append('\n');
            Files.writeString(f, sb.toString());
        } catch (Exception e) {
            System.out.println("[FarLands-G1] config write FAILED: " + e);
        }
    }

    public static void setEpoch(double x, double z) {
        epochBigX = java.math.BigDecimal.valueOf(x).toBigInteger();
        epochBigZ = java.math.BigDecimal.valueOf(z).toBigInteger();
        save();
    }

    /** Exact epoch setter (BigInteger). */
    public static void setEpoch(java.math.BigInteger x, java.math.BigInteger z) {
        epochBigX = x;
        epochBigZ = z;
        save();
    }

    /** Exact epoch (BigInteger). */
    public static java.math.BigInteger epochBigX() {
        return epochBigX;
    }

    /** Exact epoch (BigInteger). */
    public static java.math.BigInteger epochBigZ() {
        return epochBigZ;
    }

    public static boolean hasEpoch() {
        return epochBigX != null;
    }

    public static double epochX() {
        return epochBigX != null ? epochBigX.doubleValue() : Double.NaN;
    }

    public static double epochZ() {
        return epochBigZ != null ? epochBigZ.doubleValue() : Double.NaN;
    }

    public static boolean autoRelocate() {
        return autoRelocate;
    }

    public static double relocateMargin() {
        return relocateMargin;
    }

    public static long relocateDiscardOver() {
        return relocateDiscardOver;
    }

    public static int fluidTickLimit() {
        return fluidTickLimit;
    }

    public static String archiveDir() {
        return archiveDir;
    }

    public static boolean debug() {
        return debug;
    }
}
