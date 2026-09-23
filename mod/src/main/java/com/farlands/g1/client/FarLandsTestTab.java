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
 * FarLands "Test" tab on the create-world screen: the headless experiment
 * harness (testgen / testgen_stop / testgen_settle / testspawn / spawnset).
 *
 * <p>These drive real distance-phenomenon experiments without a player: force
 * generation of chunk regions, print the deterministic fingerprint, optionally
 * save and halt. Test worlds only.</p>
 */
public class FarLandsTestTab extends GridLayoutTab {

    public FarLandsTestTab() {
        super(FarLandsI18n.t("\u6d4b\u8bd5", "Test"));
        Font font = Minecraft.getInstance().font;
        Properties d = WorldDraft.get();
        GridLayout.RowHelper h = this.layout.columnSpacing(8).rowSpacing(6).createRowHelper(4);
        List<AbstractWidget> inputs = new ArrayList<>();

        h.addChild(warn(font,
            "\u26a0 \u65e0\u5934\u5b9e\u9a8c\uff1atestgen \u5f3a\u5236\u751f\u6210\u6307\u5b9a\u533a\u5757\u5e76\u6253\u5370\u6307\u7eb9\uff1b",
            "\u26a0 Headless experiments: testgen force-generates chunks and prints a fingerprint;"), 4);
        h.addChild(warn(font,
            "testgen_stop \u4f1a\u4fdd\u5b58\u5e76\u9000\u51fa\u6e38\u620f\u3002\u4ec5\u7528\u4e8e\u6d4b\u8bd5\u4e16\u754c\u3002",
            "testgen_stop saves and halts the game. Test worlds only."), 4);

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

        h.addChild(new StringWidget(
            FarLandsI18n.t("\u6d4b\u8bd5\u533a\u57df (testgen)", "Test region (testgen)"), font), 1);
        EditBox testgen = text(font, d, "testgen", "cx,cz,n;cx,cz,n", 230, inputs);
        testgen.setTooltip(Tooltip.create(FarLandsI18n.t(
            "testgen\uff1a\u5f3a\u5236\u751f\u6210\u7684\u533a\u5757\u533a\u57df\uff0c\u683c\u5f0f cx,cz,n;cx,cz,n\u3002",
            "testgen: chunk regions to force-generate, format cx,cz,n;cx,cz,n.")));
        h.addChild(testgen, 3);

        CycleButton<Boolean> stop = CycleButton.builder(
                b -> Component.literal(b ? "true" : "false"),
                Boolean.parseBoolean(d.getProperty("testgen_stop", "false")))
            .withValues(true, false)
            .create(Component.literal("testgen_stop"),
                (btn, val) -> WorldDraft.put("testgen_stop", val.toString()));
        inputs.add(stop);
        stop.setTooltip(Tooltip.create(FarLandsI18n.t(
            "testgen_stop\uff1atestgen \u540e\u7b49\u5f85\u3001\u4fdd\u5b58\u5e76\u9000\u51fa\u6e38\u620f\u3002",
            "testgen_stop: settle, save and halt after testgen.")));
        h.addChild(new StringWidget(FarLandsI18n.t("\u5b8c\u6210\u540e\u9000\u51fa", "Halt after"), font), 1);
        h.addChild(stop, 1);

        h.addChild(new StringWidget(FarLandsI18n.t("\u7b49\u5f85 tick (testgen_settle)", "Settle (testgen_settle)"), font), 1);
        EditBox settle = text(font, d, "testgen_settle", "200", 70, inputs);
        settle.setTooltip(Tooltip.create(FarLandsI18n.t(
            "testgen_settle\uff1a\u4fdd\u5b58\u524d\u7684\u7b49\u5f85 tick \u6570\uff08\u9ed8\u8ba4 200\uff09\u3002",
            "testgen_settle: settle ticks before save (default 200).")));
        h.addChild(settle, 1);

        CycleButton<Boolean> spawn = CycleButton.builder(
                b -> Component.literal(b ? "true" : "false"),
                Boolean.parseBoolean(d.getProperty("testspawn", "false")))
            .withValues(true, false)
            .create(Component.literal("testspawn"),
                (btn, val) -> WorldDraft.put("testspawn", val.toString()));
        inputs.add(spawn);
        spawn.setTooltip(Tooltip.create(FarLandsI18n.t(
            "testspawn\uff1a\u65e0\u5934\u8dd1\u51fa\u751f\u70b9\u641c\u7d22\u8def\u5f84\uff08\u590d\u73b0\u4e16\u754c\u8fdb\u5165\u5931\u8d25\uff09\u3002",
            "testspawn: run the spawn-search path headlessly (reproduces world-entry failures).")));
        h.addChild(new StringWidget(FarLandsI18n.t("\u51fa\u751f\u70b9\u641c\u7d22", "Spawn search"), font), 1);
        h.addChild(spawn, 1);

        EditBox spawnset = text(font, d, "spawnset", "x,y,z", 70, inputs);
        spawnset.setTooltip(Tooltip.create(FarLandsI18n.t(
            "spawnset\uff1a\u771f\u5b9e\u5750\u6807\u51fa\u751f\u70b9 x,y,z\uff08\u540c\u65f6\u8bbe epoch\uff09\u3002",
            "spawnset: real-coordinate spawn x,y,z (also sets the epoch).")));
        h.addChild(new StringWidget(
            FarLandsI18n.t("\u771f\u5b9e\u51fa\u751f\u70b9 (spawnset)", "Real spawn (spawnset)"), font), 1);
        h.addChild(spawnset, 1);

        for (AbstractWidget w : inputs) {
            w.active = false;
            if (w instanceof EditBox e) {
                e.setEditable(false);
            }
        }
    }

    private static StringWidget warn(Font font, String cn, String en) {
        return new StringWidget(FarLandsI18n.t(cn, en).withStyle(ChatFormatting.RED), font);
    }

    private static EditBox text(Font font, Properties d, String key, String hint, int width,
            List<AbstractWidget> inputs) {
        EditBox box = new EditBox(font, width, 20, Component.literal(key));
        box.setValue(d.getProperty(key, ""));
        box.setHint(Component.literal(hint));
        box.setResponder(v -> WorldDraft.put(key, v.trim()));
        inputs.add(box);
        return box;
    }
}
