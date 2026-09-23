package com.farlands.g1.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * FarLands tab on the create-world screen: epoch and relocation.
 *
 * <p>Inputs are disabled until the user explicitly acknowledges the manual.
 * Layout is 2 fields per row (label + field), tooltips carry the full config
 * key - the tab content area is small, so it must stay compact.</p>
 */
public class FarLandsWorldTab extends GridLayoutTab {

    public FarLandsWorldTab() {
        super(FarLandsI18n.t("FarLands", "FarLands"));
        Font font = Minecraft.getInstance().font;
        Properties d = WorldDraft.get();
        GridLayout.RowHelper h = this.layout.columnSpacing(8).rowSpacing(6).createRowHelper(4);
        List<AbstractWidget> inputs = new ArrayList<>();

        h.addChild(warn(font,
            "\u26a0 \u8bf7\u5148\u9605\u8bfb\u624b\u518c docs/CONFIG.md \u518d\u586b\u5199\uff1b",
            "\u26a0 Read the manual docs/CONFIG.md before filling;"), 4);
        h.addChild(warn(font,
            "\u9519\u8bef\u7684\u503c\u4f1a\u8ba9\u6e38\u620f\u7acb\u5373\u4e2d\u65ad\u3002epoch \u53ea\u80fd\u5728\u521b\u5efa\u524d\u8bbe\u5b9a\u3002",
            "a wrong value aborts the game; epoch is pre-creation only."), 4);

        Checkbox ack = Checkbox.builder(
                FarLandsI18n.t("\u6211\u5df2\u9605\u8bfb\u624b\u518c\uff0c\u5e76\u7167\u624b\u518c\u586b\u5199",
                    "I have read the manual and filled per it"),
                font)
            .selected(false)
            .onValueChange((cb, val) -> inputs.forEach(w -> {
                w.active = val;
                if (w instanceof EditBox e) {
                    e.setEditable(val);
                }
            }))
            .build();
        h.addChild(ack, 4);

        pair(h, font, "\u539f\u70b9X", "X",
            "epoch_x\uff1a\u771f\u5b9e\u5750\u6807\u539f\u70b9 X\uff08\u7cbe\u786e\u6574\u6570\uff0c\u652f\u6301 1e1000\uff09\u3002",
            "epoch_x: real origin X (exact integer, 1e1000 allowed).",
            text(font, d, "epoch_x", "0 / 1e1000", inputs));
        pair(h, font, "\u539f\u70b9Z", "Z",
            "epoch_z\uff1a\u771f\u5b9e\u5750\u6807\u539f\u70b9 Z\u3002",
            "epoch_z: real origin Z.",
            text(font, d, "epoch_z", "0", inputs));

        CycleButton<Boolean> auto = CycleButton.builder(
                b -> Component.literal(b ? "true" : "false"),
                Boolean.parseBoolean(d.getProperty("auto_relocate", "true")))
            .withValues(true, false)
            .create(Component.literal("auto_relocate"),
                (btn, val) -> WorldDraft.put("auto_relocate", val.toString()));
        inputs.add(auto);
        pair(h, font, "\u81ea\u52a8\u91cd\u5b9a\u4f4d", "Auto",
            "auto_relocate\uff1a\u8d70\u5230\u7a97\u53e3\u8fb9\u7f18\u65f6\u81ea\u52a8\u91cd\u5b9a\u4f4d\u3002",
            "auto_relocate: re-center automatically near the window edge.",
            auto);

        pair(h, font, "\u8fb9\u8ddd", "Margin",
            "relocate_margin\uff1a\u89e6\u53d1\u91cd\u5b9a\u4f4d\u7684\u8fb9\u8ddd\uff08\u683c\uff09\u3002",
            "relocate_margin: trigger distance from the edge (blocks).",
            text(font, d, "relocate_margin", "blocks", inputs));
        pair(h, font, "\u8c03\u8bd5", "Debug",
            "debug\uff1a0-3\uff083 \u4f1a\u4ea7\u751f\u5de8\u91cf\u65e5\u5fd7\uff09\u3002",
            "debug: 0-3 (3 = enormous logs).",
            text(font, d, "debug", "0-3", inputs));

        Button save = Button.builder(
                FarLandsI18n.t("\u4fdd\u5b58\u4e3a\u5168\u5c40\u9ed8\u8ba4", "Save as global default"),
                b -> com.farlands.g1.util.FarConfig.saveGlobalTemplate(WorldDraft.get())).build();
        inputs.add(save);
        h.addChild(save, 4);

        for (AbstractWidget w : inputs) {
            w.active = false;
            if (w instanceof EditBox e) {
                e.setEditable(false);
            }
        }
    }

    private static void pair(GridLayout.RowHelper h, Font font, String lcn, String len,
            String tcn, String ten, AbstractWidget field) {
        h.addChild(new StringWidget(FarLandsI18n.t(lcn, len), font), 1);
        field.setTooltip(Tooltip.create(FarLandsI18n.t(tcn, ten)));
        h.addChild(field, 1);
    }

    private static StringWidget warn(Font font, String cn, String en) {
        return new StringWidget(FarLandsI18n.t(cn, en).withStyle(ChatFormatting.RED), font);
    }

    private static EditBox text(Font font, Properties d, String key, String hint,
            List<AbstractWidget> inputs) {
        EditBox box = new EditBox(font, 110, 20, Component.literal(key));
        box.setValue(d.getProperty(key, ""));
        box.setHint(Component.literal(hint));
        box.setResponder(v -> WorldDraft.put(key, v.trim()));
        inputs.add(box);
        return box;
    }
}
