package com.farlands.g1.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Tiny localization for the FarLands create-world tabs.
 *
 * <p>The game language is read live: Chinese (any {@code zh*} code) gets the
 * Chinese strings, every other language gets English. This avoids shipping a
 * language file while keeping both audiences served.</p>
 */
public final class FarLandsI18n {

    private static Boolean zh;

    private FarLandsI18n() {
    }

    public static boolean zh() {
        if (zh == null) {
            boolean chinese = false;
            try {
                String code = Minecraft.getInstance().getLanguageManager().getSelected();
                chinese = code != null
                    && code.toLowerCase(java.util.Locale.ROOT).startsWith("zh");
            } catch (Throwable ignored) {
                chinese = false;
            }
            zh = chinese;
        }
        return zh;
    }

    /** Picks the Chinese or English text for the current game language. */
    public static MutableComponent t(String cn, String en) {
        return Component.literal(zh() ? cn : en);
    }
}
