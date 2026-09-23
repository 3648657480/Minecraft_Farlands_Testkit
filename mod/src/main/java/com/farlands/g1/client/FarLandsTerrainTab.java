package com.farlands.g1.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
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
 * FarLands "Terrain" tab on the create-world screen: worldgen sampling policy
 * and the pro experimental transform.
 *
 * <p>Inputs are disabled until the manual is acknowledged. All values default
 * to no-ops; tooltips carry the full config key.</p>
 */
public class FarLandsTerrainTab extends GridLayoutTab {

    public FarLandsTerrainTab() {
        super(FarLandsI18n.t("\u5730\u5f62", "Terrain"));
        Font font = Minecraft.getInstance().font;
        Properties d = WorldDraft.get();
        GridLayout.RowHelper h = this.layout.columnSpacing(8).rowSpacing(6).createRowHelper(4);
        List<AbstractWidget> inputs = new ArrayList<>();

        h.addChild(warn(font,
            "\u26a0 \u8bf7\u7167\u624b\u518c docs/CONFIG.md \u586b\u5199\uff1b\u8fd9\u4e9b\u9009\u9879\u4f1a\u6539\u53d8\u6240\u6709\u5730\u5f62\u3002",
            "\u26a0 Fill per docs/CONFIG.md; these change ALL terrain."), 4);
        h.addChild(warn(font,
            "pro \u9ed8\u8ba4\u662f no-op\uff0c\u4ec5\u6d4b\u8bd5\u4e16\u754c\u4f7f\u7528\u3002",
            "pro defaults are no-ops; test worlds only."), 4);

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

        CycleButton<String> mode = CycleButton.builder(s -> Component.literal(s),
                d.getProperty("worldgen_sample_mode", "raw"))
            .withValues("raw", "clamp", "quantize")
            .create(Component.literal("worldgen_sample_mode"),
                (btn, val) -> WorldDraft.put("worldgen_sample_mode", val));
        inputs.add(mode);
        pair(h, font, "\u6a21\u5f0f", "Mode",
            "worldgen_sample_mode\uff1araw / clamp / quantize\u3002",
            "worldgen_sample_mode: raw / clamp / quantize.",
            mode);

        pair(h, font, "\u94b3\u5236", "Clamp",
            "worldgen_sample_clamp\uff1aclamp \u6a21\u5f0f\u7684\u4e0a\u9650\u3002",
            "worldgen_sample_clamp: bound used by clamp mode.",
            text(font, d, "worldgen_sample_clamp", "1e300", inputs));
        pair(h, font, "\u9608\u503c", "Far",
            "worldgen_far_threshold\uff1a\u8d85\u8fc7\u8fd9\u4e2a\u8ddd\u79bb\u624d\u5e94\u7528\u7b56\u7565\uff080 = \u5168\u90e8\uff09\u3002",
            "worldgen_far_threshold: distance beyond which the policy applies (0 = everywhere).",
            text(font, d, "worldgen_far_threshold", "0 = everywhere", inputs));

        pair(h, font, "\u504f\u79fbX", "Off X",
            "pro_sample_offset_x\uff1a\u52a0\u5230\u566a\u58f0\u8f93\u5165 X \u7684\u504f\u79fb\uff080 = no-op\uff09\u3002",
            "pro_sample_offset_x: offset added to the noise input X (0 = no-op).",
            text(font, d, "pro_sample_offset_x", "0 = no-op", inputs));
        pair(h, font, "\u504f\u79fbZ", "Off Z",
            "pro_sample_offset_z\uff1a\u52a0\u5230\u566a\u58f0\u8f93\u5165 Z \u7684\u504f\u79fb\uff080 = no-op\uff09\u3002",
            "pro_sample_offset_z: offset added to the noise input Z (0 = no-op).",
            text(font, d, "pro_sample_offset_z", "0 = no-op", inputs));
        pair(h, font, "\u7f29\u653e", "Scale",
            "pro_sample_scale\uff1a\u566a\u58f0\u8f93\u5165\u7684\u7f29\u653e\uff081 = no-op\uff09\u3002",
            "pro_sample_scale: multiplier on the noise input (1 = no-op).",
            text(font, d, "pro_sample_scale", "1 = no-op", inputs));

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
