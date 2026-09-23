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
    // ---- worldgen (terrain sampling policy) ----
    private static volatile String worldgenSampleMode = "raw";
    private static volatile double worldgenSampleClamp = 1e300;
    private static volatile long worldgenFarThreshold = 9007199254740992L; // 2^53
    // ---- debug ----
    private static volatile int debug = 0;
    // ---- pro: experimental, HIGH IMPACT, NOT for normal players ----
    private static volatile double proSampleOffsetX = 0.0;
    private static volatile double proSampleOffsetZ = 0.0;
    private static volatile double proSampleScale = 1.0;

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
        worldgenSampleMode = "raw";
        worldgenSampleClamp = 1e300;
        worldgenFarThreshold = 9007199254740992L;
        debug = 0;
        proSampleOffsetX = 0.0;
        proSampleOffsetZ = 0.0;
        proSampleScale = 1.0;
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
        override(p, "farlands.worldgen_sample_mode", "worldgen_sample_mode");
        override(p, "farlands.worldgen_sample_clamp", "worldgen_sample_clamp");
        override(p, "farlands.worldgen_far_threshold", "worldgen_far_threshold");
        override(p, "farlands.debug", "debug");
        override(p, "farlands.pro_sample_offset_x", "pro_sample_offset_x");
        override(p, "farlands.pro_sample_offset_z", "pro_sample_offset_z");
        override(p, "farlands.pro_sample_scale", "pro_sample_scale");

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
            worldgenSampleMode = p.getProperty("worldgen_sample_mode", "raw").trim();
            worldgenSampleClamp = Double.parseDouble(p.getProperty("worldgen_sample_clamp", "1e300"));
            worldgenFarThreshold = Long.parseLong(p.getProperty("worldgen_far_threshold", "9007199254740992"));
            debug = Integer.parseInt(p.getProperty("debug", "0"));
            proSampleOffsetX = Double.parseDouble(p.getProperty("pro_sample_offset_x", "0"));
            proSampleOffsetZ = Double.parseDouble(p.getProperty("pro_sample_offset_z", "0"));
            proSampleScale = Double.parseDouble(p.getProperty("pro_sample_scale", "1"));
            // policy checks: reject invalid values outright
            if (!"true".equalsIgnoreCase(p.getProperty("auto_relocate", "true"))
                && !"false".equalsIgnoreCase(p.getProperty("auto_relocate", "true"))) {
                policyViolation("auto_relocate must be true/false, got '"
                    + p.getProperty("auto_relocate") + "'");
            }
            if (relocateMargin < 0) {
                policyViolation("relocate_margin must be >= 0, got " + (long) relocateMargin);
            }
            if (relocateDiscardOver < 1) {
                policyViolation("relocate_discard_over must be >= 1, got " + relocateDiscardOver);
            }
            if (fluidTickLimit < 0) {
                policyViolation("fluid_tick_limit must be >= 0 (0 = unlimited), got " + fluidTickLimit);
            }
            if (debug < 0 || debug > 3) {
                policyViolation("debug must be 0-3, got " + debug);
            }
            if (archiveDir.isEmpty() || archiveDir.contains("..")
                || archiveDir.contains("/") || archiveDir.contains("\\")) {
                policyViolation("archive_dir must be a plain directory name, got '" + archiveDir + "'");
            }
            if (!"raw".equals(worldgenSampleMode) && !"clamp".equals(worldgenSampleMode)
                && !"quantize".equals(worldgenSampleMode)) {
                policyViolation("worldgen_sample_mode must be raw/clamp/quantize, got '"
                    + worldgenSampleMode + "'");
            }
            if (worldgenSampleClamp <= 0 || !Double.isFinite(worldgenSampleClamp)) {
                policyViolation("worldgen_sample_clamp must be a positive finite number, got "
                    + worldgenSampleClamp);
            }
            if (worldgenFarThreshold < 0) {
                policyViolation("worldgen_far_threshold must be >= 0 (0 = everywhere), got "
                    + worldgenFarThreshold);
            }
            if (!Double.isFinite(proSampleOffsetX) || !Double.isFinite(proSampleOffsetZ)
                || !Double.isFinite(proSampleScale)) {
                policyViolation("pro_sample offsets/scale must be finite numbers");
            }
            if (proSampleScale <= 0) {
                policyViolation("pro_sample_scale must be > 0, got " + proSampleScale);
            }
        } catch (NumberFormatException e) {
            policyViolation("config parse failed: " + e.getMessage());
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

    /**
     * Policy violation: the configuration is invalid. Reject it and abort
     * the JVM immediately (fail-fast) instead of silently running with
     * defaults - a wrong epoch or a wrong margin can corrupt a world.
     */
    private static void policyViolation(String message) {
        System.out.println("[FarLands-G1] POLICY VIOLATION: " + message);
        System.out.println("[FarLands-G1] configuration rejected; aborting JVM (fail-fast).");
        System.out.flush();
        Runtime.getRuntime().halt(1);
    }

    /** Leveled logging: prints when {@code level <= debug} (debug: 0-3). */
    public static void log(int level, String message) {
        if (level <= debug) {
            System.out.println("[FarLands-G1] " + message);
            System.out.flush();
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
            sb.append("# ---- worldgen: terrain sampling policy at extreme ----\n");
            sb.append("# coordinates. EXPERIMENTAL - you own the consequences.\n");
            sb.append("#   raw      = native double sampling (default). Terrain\n");
            sb.append("#              phenomena emerge naturally.\n");
            sb.append("#   clamp    = clamp sample coordinates to +/-sample_clamp.\n");
            sb.append("#              Keeps values finite (no Infinity at >1.8e308)\n");
            sb.append("#              but terrain becomes a repeated pattern.\n");
            sb.append("#   quantize = snap sample coordinates to the 2^53 grid.\n");
            sb.append("#              Very flat/stable terrain (experiments).\n");
            sb.append("worldgen_sample_mode=").append(worldgenSampleMode).append('\n');
            sb.append("# clamp bound (clamp mode only)\n");
            sb.append("worldgen_sample_clamp=").append(worldgenSampleClamp).append('\n');
            sb.append("# distance (blocks from the epoch) beyond which the far\n");
            sb.append("# policy applies; 0 = everywhere; default 2^53\n");
            sb.append("worldgen_far_threshold=").append(worldgenFarThreshold).append('\n');
            sb.append('\n');
            sb.append("# ---- debug ----\n");
            sb.append("# 0 = off, 1 = basic events, 2 = detailed, 3 = EVERYTHING\n");
            sb.append("# WARNING: level 3 produces an ENORMOUS amount of log\n");
            sb.append("# output (per-chunk / per-tick traces). Use only for\n");
            sb.append("# short diagnostic sessions.\n");
            sb.append("debug=").append(debug).append('\n');
            sb.append('\n');
            sb.append("# ---- pro: experimental, HIGH IMPACT, not for normal players ----\n");
            sb.append("# Offsets are ADDED to the noise input coordinates (blocks);\n");
            sb.append("# scale MULTIPLIES them (>0). Defaults are strict no-ops.\n");
            sb.append("# These alter terrain generation itself (they morph the\n");
            sb.append("# terrain everywhere) - use only on fresh test worlds.\n");
            sb.append("# Change them live with: /farlands config <key> <value>\n");
            sb.append("pro_sample_offset_x=").append(proSampleOffsetX).append('\n');
            sb.append("pro_sample_offset_z=").append(proSampleOffsetZ).append('\n');
            sb.append("pro_sample_scale=").append(proSampleScale).append('\n');
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

    /** Pro: added to the noise input X coordinate (blocks). Default 0 = no-op. */
    public static double proSampleOffsetX() {
        return proSampleOffsetX;
    }

    /** Pro: added to the noise input Z coordinate (blocks). Default 0 = no-op. */
    public static double proSampleOffsetZ() {
        return proSampleOffsetZ;
    }

    /** Pro: multiplies the noise input X/Z coordinates. Default 1 = no-op. */
    public static double proSampleScale() {
        return proSampleScale;
    }

    /** One-line status for /farlands. */
    public static String status() {
        return "[FarLands] epoch=" + (epochBigX != null ? epochBigX + "/" + epochBigZ : "n/a")
            + "  debug=" + debug + "  sample=" + worldgenSampleMode
            + "  pro(off=" + proSampleOffsetX + "/" + proSampleOffsetZ
            + ", scale=" + proSampleScale + ")";
    }

    /** All keys as a multi-line list for /farlands config. */
    public static String list() {
        StringBuilder sb = new StringBuilder("[FarLands] config");
        sb.append("\n epoch_x=").append(epochBigX).append("  epoch_z=").append(epochBigZ);
        sb.append("\n auto_relocate=").append(autoRelocate)
          .append("  relocate_margin=").append((long) relocateMargin)
          .append("  relocate_discard_over=").append(relocateDiscardOver);
        sb.append("\n fluid_tick_limit=").append(fluidTickLimit).append("  archive_dir=").append(archiveDir);
        sb.append("\n worldgen_sample_mode=").append(worldgenSampleMode)
          .append("  worldgen_sample_clamp=").append(worldgenSampleClamp)
          .append("  worldgen_far_threshold=").append(worldgenFarThreshold);
        sb.append("\n debug=").append(debug);
        sb.append("\n pro_sample_offset_x=").append(proSampleOffsetX)
          .append("  pro_sample_offset_z=").append(proSampleOffsetZ)
          .append("  pro_sample_scale=").append(proSampleScale);
        return sb.toString();
    }

    /**
     * Live-set a key (validated, applied immediately and persisted). Returns a
     * human-readable result for the chat. Epoch keys are refused: changing the
     * epoch of a loaded world would desync chunks - use /realtp instead.
     */
    public static String set(String key, String value) {
        try {
            switch (key) {
                case "epoch_x", "epoch_z" -> {
                    return key + " is read-only here; use /realtp (relocation) instead";
                }
                case "auto_relocate" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        return "auto_relocate must be true/false";
                    }
                    autoRelocate = Boolean.parseBoolean(value);
                }
                case "relocate_margin" -> {
                    double v = Double.parseDouble(value);
                    if (v < 0) {
                        return "relocate_margin must be >= 0";
                    }
                    relocateMargin = v;
                }
                case "relocate_discard_over" -> {
                    long v = Long.parseLong(value);
                    if (v < 1) {
                        return "relocate_discard_over must be >= 1";
                    }
                    relocateDiscardOver = v;
                }
                case "fluid_tick_limit" -> {
                    int v = Integer.parseInt(value);
                    if (v < 0) {
                        return "fluid_tick_limit must be >= 0 (0 = unlimited)";
                    }
                    fluidTickLimit = v;
                }
                case "archive_dir" -> {
                    String v = value.trim();
                    if (v.isEmpty() || v.contains("..") || v.contains("/") || v.contains("\\")) {
                        return "archive_dir must be a plain directory name";
                    }
                    archiveDir = v;
                }
                case "worldgen_sample_mode" -> {
                    String v = value.trim();
                    if (!"raw".equals(v) && !"clamp".equals(v) && !"quantize".equals(v)) {
                        return "worldgen_sample_mode must be raw/clamp/quantize";
                    }
                    worldgenSampleMode = v;
                }
                case "worldgen_sample_clamp" -> {
                    double v = Double.parseDouble(value);
                    if (v <= 0 || !Double.isFinite(v)) {
                        return "worldgen_sample_clamp must be positive finite";
                    }
                    worldgenSampleClamp = v;
                }
                case "worldgen_far_threshold" -> {
                    long v = Long.parseLong(value);
                    if (v < 0) {
                        return "worldgen_far_threshold must be >= 0 (0 = everywhere)";
                    }
                    worldgenFarThreshold = v;
                }
                case "debug" -> {
                    int v = Integer.parseInt(value);
                    if (v < 0 || v > 3) {
                        return "debug must be 0-3";
                    }
                    debug = v;
                }
                case "pro_sample_offset_x" -> {
                    double v = Double.parseDouble(value);
                    if (!Double.isFinite(v)) {
                        return "pro_sample_offset_x must be finite";
                    }
                    proSampleOffsetX = v;
                }
                case "pro_sample_offset_z" -> {
                    double v = Double.parseDouble(value);
                    if (!Double.isFinite(v)) {
                        return "pro_sample_offset_z must be finite";
                    }
                    proSampleOffsetZ = v;
                }
                case "pro_sample_scale" -> {
                    double v = Double.parseDouble(value);
                    if (v <= 0 || !Double.isFinite(v)) {
                        return "pro_sample_scale must be > 0 and finite";
                    }
                    proSampleScale = v;
                }
                default -> {
                    return "unknown key: " + key;
                }
            }
        } catch (NumberFormatException e) {
            return "invalid value for " + key + ": '" + value + "'";
        }
        save();
        return key + " = " + value + " (applied live; persisted)";
    }

    /**
     * Re-reads farlands.properties from disk and applies the live-safe keys.
     * The epoch and relocation internals are left untouched (they are already
     * bound to the loaded world).
     */
    public static void reload() {
        Path f = file;
        if (f == null || !Files.isRegularFile(f)) {
            return;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            System.out.println("[FarLands-G1] reload read FAILED: " + e);
            return;
        }
        try {
            autoRelocate = parseBool(p, "auto_relocate", autoRelocate);
            relocateMargin = parseDouble(p, "relocate_margin", relocateMargin);
            relocateDiscardOver = parseLong(p, "relocate_discard_over", relocateDiscardOver);
            fluidTickLimit = (int) parseLong(p, "fluid_tick_limit", fluidTickLimit);
            archiveDir = p.getProperty("archive_dir", archiveDir).trim();
            worldgenSampleMode = p.getProperty("worldgen_sample_mode", worldgenSampleMode).trim();
            worldgenSampleClamp = parseDouble(p, "worldgen_sample_clamp", worldgenSampleClamp);
            worldgenFarThreshold = parseLong(p, "worldgen_far_threshold", worldgenFarThreshold);
            debug = (int) parseLong(p, "debug", debug);
            proSampleOffsetX = parseDouble(p, "pro_sample_offset_x", proSampleOffsetX);
            proSampleOffsetZ = parseDouble(p, "pro_sample_offset_z", proSampleOffsetZ);
            proSampleScale = parseDouble(p, "pro_sample_scale", proSampleScale);
            System.out.println("[FarLands-G1] config reloaded from disk");
        } catch (NumberFormatException e) {
            System.out.println("[FarLands-G1] reload failed: " + e.getMessage());
        }
    }

    private static boolean parseBool(Properties p, String key, boolean fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Boolean.parseBoolean(v.trim());
    }

    private static double parseDouble(Properties p, String key, double fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Double.parseDouble(v.trim());
    }

    private static long parseLong(Properties p, String key, long fallback) {
        String v = p.getProperty(key);
        return v == null ? fallback : Long.parseLong(v.trim());
    }

    public static String worldgenSampleMode() {
        return worldgenSampleMode;
    }

    public static double worldgenSampleClamp() {
        return worldgenSampleClamp;
    }

    public static long worldgenFarThreshold() {
        return worldgenFarThreshold;
    }

    /** Debug level 0-3 (3 = enormous log output). */
    public static int debug() {
        return debug;
    }
}
