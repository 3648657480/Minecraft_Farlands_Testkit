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
            Path epochFile = epochFile(self);
            String spec = System.getProperty("farlands.spawnset");
            if (Files.isRegularFile(epochFile)) {
                // persisted epoch wins: after a relocation the file holds the
                // new origin and a leftover -Dfarlands.spawnset must not
                // override it (observed: reload reset the epoch while the
                // world files had already been shifted).
                String[] parts = Files.readString(epochFile).trim().split(",");
                double px = Double.parseDouble(parts[0].trim());
                double pz = Double.parseDouble(parts[1].trim());
                FarProjection.setEpoch(px, pz);
                System.out.println("[FarLands-G1] EPOCH set to real (" + px + "," + pz
                    + ") from farlands_epoch.txt");
                farlands$forceLocalRespawn(self);
            } else if (spec != null && !spec.isEmpty()) {
                String[] parts = spec.split(",");
                double px = Double.parseDouble(parts[0].trim());
                double pz = Double.parseDouble(parts[2].trim());
                FarProjection.setEpoch(px, pz);
                try {
                    Files.writeString(epochFile, px + "," + pz);
                } catch (Throwable t) {
                    System.out.println("[FarLands-G1] epoch persist FAILED: " + t);
                }
                System.out.println("[FarLands-G1] EPOCH set to real (" + px + "," + pz
                    + ") from farlands.spawnset (persisted)");
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

    private static Path epochFile(MinecraftServer self) {
        return ((MinecraftServerAccessor) self).farlands$storageSource()
            .getLevelDirectory().path().resolve("farlands_epoch.txt");
    }
}
