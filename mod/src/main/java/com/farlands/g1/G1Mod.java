package com.farlands.g1;

import com.farlands.g1.runtime.Watchdog;
import net.fabricmc.api.ModInitializer;

public class G1Mod implements ModInitializer {
    public void onInitialize() {
        System.out.println("[FarLands-G1] v3.2 epoch build (realtp + relocate)");
        Watchdog.start();
    }
}
