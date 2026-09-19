package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E line: sets the fixed epoch origin from the world's saved respawn point
 * at the earliest possible moment - before {@code createLevels} loads any
 * chunk. The epoch then stays constant for the whole session, so every int
 * domain (chunk keys, sections, tickets, generation) is expressed in one
 * consistent local domain.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerEpochMixin {

    @Inject(method = "createLevels", at = @At("HEAD"))
    private void farlands$setEpoch(CallbackInfo ci) {
        try {
            MinecraftServer self = (MinecraftServer) (Object) this;
            String spec = System.getProperty("farlands.spawnset");
            if (spec != null && !spec.isEmpty()) {
                String[] parts = spec.split(",");
                double px = Double.parseDouble(parts[0].trim());
                double pz = Double.parseDouble(parts[2].trim());
                FarProjection.setEpoch(px, pz);
                System.out.println("[FarLands-G1] EPOCH set to real (" + px + "," + pz
                    + ") from farlands.spawnset");
                try {
                    LevelData.RespawnData localSpawn = new LevelData.RespawnData(
                        net.minecraft.core.GlobalPos.of(net.minecraft.world.level.Level.OVERWORLD,
                            new BlockPos(0, 100, 0)), 0.0f, 0.0f);
                    self.getWorldData().overworldData().setSpawn(localSpawn);
                    LevelData.RespawnData after = self.getWorldData().overworldData().getRespawnData();
                    System.out.println("[FarLands-G1] respawn forced to local origin (0,100,0); readback="
                        + (after != null ? after.pos() : "null"));
                } catch (Throwable t) {
                    System.out.println("[FarLands-G1] respawn override FAILED: " + t);
                }
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
}
