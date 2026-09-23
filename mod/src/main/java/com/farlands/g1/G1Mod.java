package com.farlands.g1;

import com.farlands.g1.runtime.Watchdog;
import com.farlands.g1.util.FarConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class G1Mod implements ModInitializer {
    public void onInitialize() {
        // Load the global template BEFORE any world exists: configuration must be
        // usable before world creation, not invented when the world starts.
        FarConfig.loadGlobalTemplate(FabricLoader.getInstance().getConfigDir());
        System.out.println("[FarLands-G1] " + FarConfig.buildVersion()
            + " epoch build (realtp + relocate)");
        Watchdog.start();
    }
}
