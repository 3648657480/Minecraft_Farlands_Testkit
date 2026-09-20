package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * E line: sets the fixed epoch origin before {@code createLevels} loads any
 * chunk, so every int domain is one consistent local domain.
 *
 * <p>Priority: {@code -Dfarlands.spawnset} (also persisted) &gt; the world's
 * {@code farlands_epoch.txt} &gt; the saved respawn point.</p>
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerEpochMixin {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void farlands$setEpoch(CallbackInfo ci) {
        try {
            MinecraftServer self = (MinecraftServer) (Object) this;
            Path worldDir = ((MinecraftServerAccessor) self).farlands$storageSource()
                .getLevelDirectory().path();
            com.farlands.g1.util.FarConfig.load(worldDir);
            if (com.farlands.g1.util.FarConfig.hasEpoch()) {
                FarProjection.setEpoch(com.farlands.g1.util.FarConfig.epochX(),
                    com.farlands.g1.util.FarConfig.epochZ());
                System.out.println("[FarLands-G1] EPOCH set to real ("
                    + com.farlands.g1.util.FarConfig.epochX() + ","
                    + com.farlands.g1.util.FarConfig.epochZ() + ") from farlands.properties");
                farlands$forceLocalRespawn(self);
            } else {
                LevelData.RespawnData rd = self.getWorldData().overworldData().getRespawnData();
                if (rd != null) {
                    BlockPos pos = rd.globalPos().pos();
                    FarProjection.setEpoch((long) pos.getX(), (long) pos.getZ());
                    System.out.println("[FarLands-G1] EPOCH set to real (" + pos.getX() + "," + pos.getZ() + ")");
                }
            }
        } catch (Throwable t) {
            System.out.println("[FarLands-G1] EPOCH set FAILED: " + t);
        }
        System.out.flush();
    }

    private static void farlands$forceLocalRespawn(MinecraftServer self) {
        try {
            LevelData.RespawnData localSpawn = new LevelData.RespawnData(
                net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD,
                    new BlockPos(0, 100, 0)), 0.0f, 0.0f);
            self.getWorldData().overworldData().setSpawn(localSpawn);
            System.out.println("[FarLands-G1] respawn forced to local origin (0,100,0)");
        } catch (Throwable t) {
            System.out.println("[FarLands-G1] respawn override FAILED: " + t);
        }
    }

}
