package com.farlands.g1.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.PreferredGraphicsApi;

/**
 * UNLOCK (opt-in lab tool): logs the "我知道我在做什么" banner and the Vulkan
 * gate warning at client startup. Silent unless BOTH unlock flags are set
 * ({@code -Dfarlands.unlock} and {@code -Dfarlands.unlock.i_know_what_im_doing}).
 *
 * <p>Vertex-count limits are only truly lifted by the Vulkan backend; under
 * OpenGL the driver still caps indices. See docs/UNLOCK-DESIGN.md.</p>
 */
public final class UnlockWarn {

    private static boolean done = false;

    private UnlockWarn() {
    }

    public static void check(Minecraft mc) {
        if (done) {
            return;
        }
        done = true;
        if (!(Boolean.getBoolean("farlands.unlock")
                && Boolean.getBoolean("farlands.unlock.i_know_what_im_doing"))) {
            return;
        }
        System.out.println("[FarLands-G1] UNLOCK ENABLED \u2014 \u6211\u77e5\u9053\u6211\u5728\u505a\u4ec0\u4e48"
            + "\uff08\u6781\u9650\u6d4b\u8bd5\u6a21\u5f0f\uff1b\u540e\u679c\u81ea\u62c5\uff09");
        try {
            PreferredGraphicsApi api = mc.options.preferredGraphicsBackend().get();
            if (api == PreferredGraphicsApi.VULKAN) {
                System.out.println("[FarLands-G1] UNLOCK: Graphics API = VULKAN \u2192 "
                    + "\u9876\u70b9\u9650\u5236\u89e3\u9664\u751f\u6548");
            } else {
                System.out.println("[FarLands-G1] UNLOCK: \u9876\u70b9\u9650\u5236\u4e0d\u4f1a\u5931\u6548\uff01"
                    + "\u5f53\u524d Graphics API = " + api
                    + "\uff1b\u8bf7\u5728 \u9009\u9879 \u2192 \u56fe\u5f62 \u2192 Graphics API \u9009\u62e9 Vulkan");
            }
        } catch (Throwable t) {
            System.out.println("[FarLands-G1] UNLOCK: \u65e0\u6cd5\u8bfb\u53d6 Graphics API: " + t);
        }
        System.out.flush();
    }
}
