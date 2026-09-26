package com.farlands.g1.client;

import com.farlands.g1.util.FarConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The pending configuration for the world about to be created.
 *
 * <p>The FarLands tabs on the create-world screen edit this. When the world
 * folder is created it is written to {@code <world>/farlands.properties}, so
 * configuration exists BEFORE terrain generation - and before the world is ever
 * entered. Dedicated servers have no screen and fall back to the global
 * template.</p>
 */
public final class WorldDraft {

    private static final String[] KEYS = {
        "epoch_x", "epoch_z",
        "auto_relocate", "relocate_margin",
        "fluid_tick_limit", "archive_dir",
        "worldgen_sample_mode", "worldgen_sample_clamp", "worldgen_far_threshold",
        "debug",
        "pro_sample_offset_x", "pro_sample_offset_z", "pro_sample_scale",
        "testgen", "testgen_stop", "testgen_settle", "testspawn", "spawnset"
    };

    private static Properties draft;

    private WorldDraft() {
    }

    /** The shared draft, seeded once from the global template. */
    public static Properties get() {
        if (draft == null) {
            Properties t = FarConfig.globalTemplate();
            draft = t != null ? (Properties) t.clone() : new Properties();
            FarConfig.setDraft(draft);
        }
        return draft;
    }

    public static void put(String key, String value) {
        get().setProperty(key, value);
        FarConfig.setDraft(draft);
    }

    /**
     * Writes the configuration into a new world folder. Always produces
     * {@code <world>/farlands.properties}: if the FarLands tabs were never
     * touched ({@code draft == null}), it falls back to the global template, so
     * every new world gets a config file by default.
     */
    public static void writeTo(Path worldDir) {
        try {
            Properties p = draft;
            if (p == null) {
                Properties t = FarConfig.globalTemplate();
                p = t != null ? (Properties) t.clone() : new Properties();
            }
            StringBuilder sb = new StringBuilder();
            sb.append("# FarLands G1 - world configuration (set on the create-world screen)\n");
            sb.append("# Edit this file and re-enter the world; see docs/CONFIG.md.\n");
            for (String key : KEYS) {
                String v = p.getProperty(key);
                if (v != null) {
                    sb.append(key).append('=').append(v).append('\n');
                }
            }
            Path file = worldDir.resolve("farlands.properties");
            Files.writeString(file, sb.toString());
            System.out.println("[FarLands-G1] wrote world config: " + file
                + (draft == null ? " (from global template)" : " (from create-world draft)"));
        } catch (Exception e) {
            System.out.println("[FarLands-G1] draft write FAILED: " + e);
        } finally {
            draft = null;
            FarConfig.setDraft(null);
        }
    }
}
