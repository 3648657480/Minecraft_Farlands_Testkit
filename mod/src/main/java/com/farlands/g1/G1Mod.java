package com.farlands.g1;

import com.farlands.g1.runtime.Watchdog;
import net.fabricmc.api.ModInitializer;

public class G1Mod implements ModInitializer {
    public void onInitialize() {
        System.out.println("[FarLands-G1] v3.0 epoch build (E line)");
        Watchdog.start();
    }
}
