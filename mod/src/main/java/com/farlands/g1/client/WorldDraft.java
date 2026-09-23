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
        "auto_relocate", "relocate_margin", "relocate_discard_over",
        "fluid_tick_limit", "archive_dir",
        "worldgen_sample_mode", "worldgen_sample_clamp", "worldgen_far_threshold",
        "debug",
        "pro_sample_offset_x", "pro_sample_offset_z", "pro_sample_scale"
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

    /** Writes the draft into a new world folder; no-op if the tabs were unused. */
    public static void writeTo(Path worldDir) {
        if (draft == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# FarLands G1 - world configuration (set on the create-world screen)\n");
            sb.append("# Edit this file and re-enter the world; see docs/CONFIG.md.\n");
            for (String key : KEYS) {
                String v = draft.getProperty(key);
                if (v != null) {
                    sb.append(key).append('=').append(v).append('\n');
                }
            }
            Files.writeString(worldDir.resolve("farlands.properties"), sb.toString());
            System.out.println("[FarLands-G1] wrote world config from the create-world draft");
        } catch (Exception e) {
            System.out.println("[FarLands-G1] draft write FAILED: " + e);
        } finally {
            draft = null;
            FarConfig.setDraft(null);
        }
    }
}
