package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E line: the vanilla initial-spawn search samples the climate at the
 * epoch coordinate (which is out of the local domain) and writes a huge /
 * wrapped spawn chunk into the level data. The spawn area is then
 * generated at that mismatched chunk while the player-spawn flow uses the
 * local origin - the two domains split and chunk generation crashes with
 * acquireGeneration NPEs.
 *
 * <p>Under an active epoch the spawn IS the local origin: the respawn has
 * already been forced to (0,100,0) in the local domain before this runs,
 * so skip the climate search entirely.</p>
 */
@Mixin(MinecraftServer.class)
public class SetInitialSpawnEpochMixin {

    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void farlands$skipSpawnSearch(ServerLevel level, ServerLevelData levelData,
            boolean spawnBonusChest, boolean isDebug, LevelLoadListener levelLoadListener, CallbackInfo ci) {
        if (FarProjection.isEpochActive()) {
            System.out.println("[FarLands-G1] setInitialSpawn skipped (epoch active, spawn = local origin)");
            System.out.flush();
            ci.cancel();
        }
    }
}
