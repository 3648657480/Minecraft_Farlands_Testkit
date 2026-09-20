package com.farlands.g1.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * E line: per-world configuration ({@code farlands.properties} in the world
 * directory). JVM properties override the file, which overrides defaults.
 *
 * <pre>
 *   epoch_x=10000000000.0     world origin (real coordinates)
 *   epoch_z=0.0
 *   auto_relocate=true        re-center automatically near the window edge
 *   relocate_margin=100000    trigger distance from the int window edge (blocks)
 * </pre>
 */
public final class FarConfig {

    private static volatile double epochX = Double.NaN;
    private static volatile double epochZ = Double.NaN;
    private static volatile boolean autoRelocate = false;
    private static volatile double relocateMargin = 100_000.0;
    private static volatile Path file;

    private FarConfig() {
    }

    /** Loads the config for a world; call before the epoch is applied. */
    public static void load(Path worldDir) {
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
        String auto = System.getProperty("farlands.auto_relocate");
        if (auto != null) {
            p.setProperty("auto_relocate", auto);
        }
        String margin = System.getProperty("farlands.relocate_margin");
        if (margin != null) {
            p.setProperty("relocate_margin", margin);
        }

        try {
            if (p.containsKey("epoch_x")) {
                epochX = Double.parseDouble(p.getProperty("epoch_x").trim());
                epochZ = Double.parseDouble(p.getProperty("epoch_z", "0").trim());
            }
            autoRelocate = Boolean.parseBoolean(p.getProperty("auto_relocate", "false"));
            relocateMargin = Double.parseDouble(p.getProperty("relocate_margin", "100000"));
        } catch (NumberFormatException e) {
            System.out.println("[FarLands-G1] config parse FAILED: " + e);
        }
        if (spawnset != null || !Files.isRegularFile(file)) {
            save();
        }
    }

    /** Persists the current values (called after epoch changes). */
    public static void save() {
        Path f = file;
        if (f == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# FarLands G1 world configuration\n");
            if (!Double.isNaN(epochX)) {
                sb.append("epoch_x=").append(epochX).append('\n');
                sb.append("epoch_z=").append(epochZ).append('\n');
            }
            sb.append("auto_relocate=").append(autoRelocate).append('\n');
            sb.append("relocate_margin=").append((long) relocateMargin).append('\n');
            Files.writeString(f, sb.toString());
        } catch (Exception e) {
            System.out.println("[FarLands-G1] config write FAILED: " + e);
        }
    }

    public static void setEpoch(double x, double z) {
        epochX = x;
        epochZ = z;
        save();
    }

    public static boolean hasEpoch() {
        return !Double.isNaN(epochX);
    }

    public static double epochX() {
        return epochX;
    }

    public static double epochZ() {
        return epochZ;
    }

    public static boolean autoRelocate() {
        return autoRelocate;
    }

    public static double relocateMargin() {
        return relocateMargin;
    }
}
