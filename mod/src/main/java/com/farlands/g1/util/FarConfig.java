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
    // ---- test harness: headless distance-phenomenon experiments ----
    private static volatile String testgen = null;
    private static volatile boolean testgenStop = false;
    private static volatile int testgenSettle = 200;
    private static volatile boolean testspawn = false;
    private static volatile String spawnset = null;

    private static volatile Path file;
    private static volatile Path globalFile;
    private static volatile Properties globalTemplate;
    private static volatile Properties draft;

    /**
     * Canonical key order. Every config source (global template, world draft,
     * per-world file) uses these names.
     */
    private static final String[] KEYS = {
        "epoch_x", "epoch_z",
        "auto_relocate", "relocate_margin",
        "fluid_tick_limit", "archive_dir",
        "worldgen_sample_mode", "worldgen_sample_clamp", "worldgen_far_threshold",
        "debug",
        "pro_sample_offset_x", "pro_sample_offset_z", "pro_sample_scale",
        "testgen", "testgen_stop", "testgen_settle", "testspawn", "spawnset"
    };

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
        fluidTickLimit = 2000;
        archiveDir = "farlands_epochs";
        worldgenSampleMode = "raw";
        worldgenSampleClamp = 1e300;
        worldgenFarThreshold = 9007199254740992L;
        debug = 0;
        proSampleOffsetX = 0.0;
        proSampleOffsetZ = 0.0;
        proSampleScale = 1.0;
        testgen = null;
        testgenStop = false;
        testgenSettle = 200;
        testspawn = false;
        spawnset = null;
        FarProjection.resetEpoch();
        file = worldDir.resolve("farlands.properties");
        Properties p = new Properties();
        boolean fresh = !Files.isRegularFile(file);
        if (fresh) {
            // Brand-new world: seed from the world-creation draft (the FarLands
            // tabs on the create-world screen) or, failing that, the global
            // template. Configuration is therefore available BEFORE the world
            // is generated - it is never invented out of nothing here.
            Properties seed = draft != null ? draft : globalTemplate;
            if (seed != null) {
                for (String k : KEYS) {
                    String v = seed.getProperty(k);
                    if (v != null) {
                        p.setProperty(k, v);
                    }
                }
            }
        } else {
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
        // spawnset: JVM flag > world-file key; sets the epoch (and respawn)
        String spawnsetSpec = System.getProperty("farlands.spawnset");
        if (spawnsetSpec == null || spawnsetSpec.isEmpty()) {
            spawnsetSpec = p.getProperty("spawnset", "");
        }
        if (!spawnsetSpec.isEmpty()) {
            String[] parts = spawnsetSpec.split(",");
            if (parts.length >= 3) {
                p.setProperty("epoch_x", parts[0].trim());
                p.setProperty("epoch_z", parts[2].trim());
            }
        }
        override(p, "farlands.auto_relocate", "auto_relocate");
        override(p, "farlands.relocate_margin", "relocate_margin");
        override(p, "farlands.fluid_tick_limit", "fluid_tick_limit");
        override(p, "farlands.archive_dir", "archive_dir");
        override(p, "farlands.worldgen_sample_mode", "worldgen_sample_mode");
        override(p, "farlands.worldgen_sample_clamp", "worldgen_sample_clamp");
        override(p, "farlands.worldgen_far_threshold", "worldgen_far_threshold");
        override(p, "farlands.debug", "debug");
        override(p, "farlands.pro_sample_offset_x", "pro_sample_offset_x");
        override(p, "farlands.pro_sample_offset_z", "pro_sample_offset_z");
        override(p, "farlands.pro_sample_scale", "pro_sample_scale");
        override(p, "farlands.testgen", "testgen");
        override(p, "farlands.testgen.stop", "testgen_stop");
        override(p, "farlands.testgen.settle", "testgen_settle");
        override(p, "farlands.testspawn", "testspawn");

        try {
            if (p.containsKey("epoch_x")) {
                epochBigX = new java.math.BigDecimal(p.getProperty("epoch_x").trim()).toBigInteger();
                epochBigZ = new java.math.BigDecimal(p.getProperty("epoch_z", "0").trim()).toBigInteger();
            }
            autoRelocate = Boolean.parseBoolean(p.getProperty("auto_relocate", "true"));
            relocateMargin = Double.parseDouble(p.getProperty("relocate_margin", "100000"));
            fluidTickLimit = Integer.parseInt(p.getProperty("fluid_tick_limit", "2000"));
            archiveDir = p.getProperty("archive_dir", "farlands_epochs").trim();
            worldgenSampleMode = p.getProperty("worldgen_sample_mode", "raw").trim();
            worldgenSampleClamp = Double.parseDouble(p.getProperty("worldgen_sample_clamp", "1e300"));
            worldgenFarThreshold = Long.parseLong(p.getProperty("worldgen_far_threshold", "9007199254740992"));
            debug = Integer.parseInt(p.getProperty("debug", "0"));
            proSampleOffsetX = Double.parseDouble(p.getProperty("pro_sample_offset_x", "0"));
            proSampleOffsetZ = Double.parseDouble(p.getProperty("pro_sample_offset_z", "0"));
            proSampleScale = Double.parseDouble(p.getProperty("pro_sample_scale", "1"));
            testgen = trimOrNull(p.getProperty("testgen"));
            testgenStop = Boolean.parseBoolean(p.getProperty("testgen_stop", "false"));
            testgenSettle = Integer.parseInt(p.getProperty("testgen_settle", "200"));
            testspawn = Boolean.parseBoolean(p.getProperty("testspawn", "false"));
            spawnset = trimOrNull(p.getProperty("spawnset"));
            // policy checks: reject invalid values outright
            if (!"true".equalsIgnoreCase(p.getProperty("auto_relocate", "true"))
                && !"false".equalsIgnoreCase(p.getProperty("auto_relocate", "true"))) {
                policyViolation("auto_relocate must be true/false, got '"
                    + p.getProperty("auto_relocate") + "'");
            }
            if (relocateMargin < 0) {
                policyViolation("relocate_margin must be >= 0, got " + (long) relocateMargin);
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
            if (testgenSettle < 0) {
                policyViolation("testgen_settle must be >= 0, got " + testgenSettle);
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
        if ((spawnsetSpec != null && !spawnsetSpec.isEmpty()) || fresh) {
            save();
            draft = null;
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static void override(Properties p, String jvmKey, String fileKey) {
        String v = System.getProperty(jvmKey);
        if (v != null && !v.isEmpty()) {
            p.setProperty(fileKey, v);
        }
    }

    /** Default value for every key (the seed for the global template). */
    private static Properties defaultsProperties() {
        Properties p = new Properties();
        p.setProperty("epoch_x", "0");
        p.setProperty("epoch_z", "0");
        p.setProperty("auto_relocate", "true");
        p.setProperty("relocate_margin", "100000");
        p.setProperty("fluid_tick_limit", "2000");
        p.setProperty("archive_dir", "farlands_epochs");
        p.setProperty("worldgen_sample_mode", "raw");
        p.setProperty("worldgen_sample_clamp", "1e300");
        p.setProperty("worldgen_far_threshold", "9007199254740992");
        p.setProperty("debug", "0");
        p.setProperty("pro_sample_offset_x", "0");
        p.setProperty("pro_sample_offset_z", "0");
        p.setProperty("pro_sample_scale", "1");
        p.setProperty("testgen", "");
        p.setProperty("testgen_stop", "false");
        p.setProperty("testgen_settle", "200");
        p.setProperty("testspawn", "false");
        p.setProperty("spawnset", "");
        return p;
    }

    /**
     * Loads (or creates) the global template at
     * {@code config/farlands-g1.properties}. Called at mod init, before any
     * world exists: this is how configuration becomes available BEFORE world
     * creation. New worlds are seeded from it (or from the create-world UI).
     */
    public static void loadGlobalTemplate(Path configDir) {
        try {
            Files.createDirectories(configDir);
            globalFile = configDir.resolve("farlands-g1.properties");
            Properties p = defaultsProperties();
            boolean created = false;
            if (Files.isRegularFile(globalFile)) {
                try (var in = Files.newInputStream(globalFile)) {
                    p.load(in);
                } catch (Exception e) {
                    System.out.println("[FarLands-G1] global template read FAILED: " + e);
                }
            } else {
                saveGlobalTemplate(p);
                created = true;
            }
            // keep the annotated file complete: if a newer version added keys,
            // rewrite so the user can see and edit them
            if (!created) {
                boolean missing = false;
                for (String k : KEYS) {
                    if (!p.containsKey(k)) {
                        missing = true;
                        break;
                    }
                }
                if (missing) {
                    saveGlobalTemplate(p);
                }
            }
            globalTemplate = p;
            System.out.println("[FarLands-G1] global template loaded: " + globalFile);
        } catch (Exception e) {
            globalTemplate = defaultsProperties();
            System.out.println("[FarLands-G1] global template FAILED: " + e);
        }
    }

    /** The global template (defaults for new worlds); never null after init. */
    public static Properties globalTemplate() {
        return globalTemplate;
    }

    /** The pending create-world draft (client UI); null when none. */
    public static Properties draft() {
        return draft;
    }

    /** Sets the pending create-world draft; consumed by the next fresh load. */
    public static void setDraft(Properties p) {
        draft = p;
    }

    /** Writes a Properties as the global template (annotated). */
    public static void saveGlobalTemplate(Properties p) {
        Path f = globalFile;
        if (f == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ============================================================\n");
            sb.append("#  FarLands G1 - GLOBAL TEMPLATE\n");
            sb.append("#  Defaults for NEW worlds. Edit this file before creating a\n");
            sb.append("#  world (or use the FarLands tabs on the create-world screen).\n");
            sb.append("#  Per-world farlands.properties overrides this once created.\n");
            sb.append("#  JVM flags override this file: -Dfarlands.<key>=<value>\n");
            sb.append("# ============================================================\n");
            sb.append('\n');
            for (String k : KEYS) {
                String v = p.getProperty(k);
                if (v != null) {
                    sb.append(k).append('=').append(v).append('\n');
                }
            }
            Files.writeString(f, sb.toString());
        } catch (Exception e) {
            System.out.println("[FarLands-G1] global template write FAILED: " + e);
        }
    }

    /**
     * The single build version, read from the mod metadata (which the build
     * fills from the repo-root {@code VERSION} file). Every banner and doc
     * must agree with this - it is the authoritative version source.
     */
    public static String buildVersion() {
        try {
            return net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("farlands-g1")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
        } catch (Throwable t) {
            return "dev";
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
            sb.append("# Set these on the world-creation screen (FarLands tab).\n");
            sb.append("pro_sample_offset_x=").append(proSampleOffsetX).append('\n');
            sb.append("pro_sample_offset_z=").append(proSampleOffsetZ).append('\n');
            sb.append("pro_sample_scale=").append(proSampleScale).append('\n');
            sb.append('\n');
            sb.append("# ---- test harness: headless distance-phenomenon experiments ----\n");
            sb.append("# testgen forces generation of chunk regions and prints a\n");
            sb.append("# deterministic fingerprint (wgHash / topY / biome / blocks).\n");
            sb.append("# Format: cx,cz,n;cx,cz,n  (n = n x n region, default 1).\n");
            sb.append("# testgen_stop=true settles, saves and halts the game after.\n");
            sb.append("# testspawn runs the spawn-search path headlessly.\n");
            sb.append("# spawnset=x,y,z sets the real spawn/epoch. Test worlds only.\n");
            sb.append("testgen=").append(testgen == null ? "" : testgen).append('\n');
            sb.append("testgen_stop=").append(testgenStop).append('\n');
            sb.append("testgen_settle=").append(testgenSettle).append('\n');
            sb.append("testspawn=").append(testspawn).append('\n');
            sb.append("spawnset=").append(spawnset == null ? "" : spawnset).append('\n');
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

    /** testgen spec "cx,cz,n;cx,cz,n", or null when disabled. */
    public static String testgen() {
        return testgen;
    }

    /** After testgen: settle, save and halt the game. */
    public static boolean testgenStop() {
        return testgenStop;
    }

    /** Settle ticks before save/halt (default 200). */
    public static int testgenSettle() {
        return testgenSettle;
    }

    /** Run the headless spawn-search path. */
    public static boolean testspawn() {
        return testspawn;
    }

    /** spawnset "x,y,z" (real coordinates), or null. */
    public static String spawnset() {
        return spawnset;
    }
}
