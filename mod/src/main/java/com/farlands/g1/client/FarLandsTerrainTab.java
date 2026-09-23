package com.farlands.g1.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.network.chat.Component;

import java.util.Properties;

/**
 * FarLands tab on the create-world screen: worldgen sampling policy and the
 * pro experimental transform.
 *
 * <p>All values default to strict no-ops. They feed {@link WorldDraft} and are
 * written into the new world before generation.</p>
 */
public class FarLandsTerrainTab extends GridLayoutTab {

    public FarLandsTerrainTab() {
        super(Component.literal("Terrain"));
        Font font = Minecraft.getInstance().font;
        Properties d = WorldDraft.get();
        GridLayout.RowHelper helper = this.layout.columnSpacing(10).rowSpacing(8).createRowHelper(2);

        CycleButton<String> mode = CycleButton.builder(s -> Component.literal(s),
                d.getProperty("worldgen_sample_mode", "raw"))
            .withValues("raw", "clamp", "quantize")
            .create(Component.literal("worldgen_sample_mode"),
                (btn, val) -> WorldDraft.put("worldgen_sample_mode", val));
        helper.addChild(CommonLayouts.labeledElement(font, mode,
            Component.literal("worldgen_sample_mode")), 2);

        addText(helper, font, d, "worldgen_sample_clamp", "clamp bound (clamp mode)");
        addText(helper, font, d, "worldgen_far_threshold", "far threshold (0 = everywhere)");
        addText(helper, font, d, "pro_sample_offset_x", "pro offset X (0 = no-op)");
        addText(helper, font, d, "pro_sample_offset_z", "pro offset Z (0 = no-op)");
        addText(helper, font, d, "pro_sample_scale", "pro scale (1 = no-op)");
    }

    private static void addText(GridLayout.RowHelper helper, Font font, Properties d,
            String key, String hint) {
        EditBox box = new EditBox(font, 150, 20, Component.literal(key));
        box.setValue(d.getProperty(key, ""));
        box.setHint(Component.literal(hint));
        box.setResponder(v -> WorldDraft.put(key, v.trim()));
        helper.addChild(CommonLayouts.labeledElement(font, box, Component.literal(key)), 2);
    }
}
