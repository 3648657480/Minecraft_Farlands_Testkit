package com.farlands.g1.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.network.chat.Component;

import java.util.Properties;

/**
 * FarLands tab on the create-world screen: epoch and relocation.
 *
 * <p>Everything here feeds {@link WorldDraft}, which is written into the new
 * world folder before generation. This is the "before world creation" entry
 * point - configuration is never edited from inside a running world.</p>
 */
public class FarLandsWorldTab extends GridLayoutTab {

    public FarLandsWorldTab() {
        super(Component.literal("FarLands"));
        Font font = Minecraft.getInstance().font;
        Properties d = WorldDraft.get();
        GridLayout.RowHelper helper = this.layout.columnSpacing(10).rowSpacing(8).createRowHelper(2);

        helper.addChild(CommonLayouts.labeledElement(font,
            text(font, d, "epoch_x", "epoch_x (exact, e.g. 1e1000)"),
            Component.literal("epoch_x")), 2);
        helper.addChild(CommonLayouts.labeledElement(font,
            text(font, d, "epoch_z", "epoch_z"),
            Component.literal("epoch_z")), 2);

        CycleButton<Boolean> auto = CycleButton.builder(
                b -> Component.literal(b ? "true" : "false"),
                Boolean.parseBoolean(d.getProperty("auto_relocate", "true")))
            .withValues(true, false)
            .create(Component.literal("auto_relocate"),
                (btn, val) -> WorldDraft.put("auto_relocate", val.toString()));
        helper.addChild(CommonLayouts.labeledElement(font, auto,
            Component.literal("auto_relocate")), 2);

        helper.addChild(CommonLayouts.labeledElement(font,
            text(font, d, "relocate_margin", "relocate_margin (blocks)"),
            Component.literal("relocate_margin")), 2);
        helper.addChild(CommonLayouts.labeledElement(font,
            text(font, d, "debug", "debug 0-3"),
            Component.literal("debug")), 2);

        Button save = Button.builder(Component.literal("Save as global default"), b ->
            com.farlands.g1.util.FarConfig.saveGlobalTemplate(WorldDraft.get())).build();
        helper.addChild(save, 2);
    }

    private static EditBox text(Font font, Properties d, String key, String hint) {
        EditBox box = new EditBox(font, 150, 20, Component.literal(key));
        box.setValue(d.getProperty(key, ""));
        box.setHint(Component.literal(hint));
        box.setResponder(v -> WorldDraft.put(key, v.trim()));
        return box;
    }
}
