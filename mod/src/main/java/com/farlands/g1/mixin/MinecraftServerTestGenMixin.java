package com.farlands.g1.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Headless test hook: when the {@code farlands.testgen} system property is
 * set (format {@code chunkX,chunkZ}), forces a FULL generation of that
 * chunk once the server ticks and prints the result. Lets the E line
 * iterate on the generation domain without a client or a player.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerTestGenMixin {

    @Unique
    private static boolean farlands$testgenDone = false;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void farlands$testgen(BooleanSupplier hasTime, CallbackInfo ci) {
        if (!farlands$testgenDone) {
            farlands$testgenDone = true;
            runTestGen((MinecraftServer) (Object) this);
        }
        if (!farlands$spawnDone) {
            farlands$spawnDone = true;
            runSpawnSet((MinecraftServer) (Object) this);
        }
    }

    @Unique
    private static boolean farlands$spawnDone = false;

    @Unique
    private static void runSpawnSet(MinecraftServer self) {
        String spec = System.getProperty("farlands.spawnset");
        if (spec == null || spec.isEmpty()) return;
        ServerLevel level = self.overworld();
        try {
            String[] parts = spec.split(",");
            long px = Long.parseLong(parts[0].trim());
            int py = Integer.parseInt(parts[1].trim());
            long pz = Long.parseLong(parts[2].trim());
            long epochX = (px >> 4) << 4;
            long epochZ = (pz >> 4) << 4;
            com.farlands.g1.util.FarProjection.setEpoch(epochX, epochZ);
            int localX = (int) (px - epochX);
            int localZ = (int) (pz - epochZ);
            level.setRespawnData(new net.minecraft.world.level.storage.LevelData.RespawnData(
                net.minecraft.core.GlobalPos.of(
                    net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.core.BlockPos(localX, py, localZ)), 0.0f, 0.0f));
            System.out.println("[FarLands-Test] spawn set: real=(" + px + "," + py + "," + pz
                + ") epoch=(" + epochX + "," + epochZ + ") local=(" + localX + "," + localZ + ")");
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] spawn set FAILED: " + t);
        }
        System.out.flush();
    }

    @Unique
    private static void runTestGen(MinecraftServer self) {
        String spec = System.getProperty("farlands.testgen");
        if (spec == null || spec.isEmpty()) return;
        ServerLevel level = self.overworld();
        try {
            String[] parts = spec.split(",");
            int cx = Integer.parseInt(parts[0].trim());
            int cz = Integer.parseInt(parts[1].trim());
            ChunkAccess chunk = level.getChunk(cx, cz);
            int top = chunk != null ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) : -999;
            StringBuilder sb = new StringBuilder();
            sb.append("[FarLands-Test] gen OK chunk=(").append(cx).append(",").append(cz)
                .append(") topY=").append(top);
            if (chunk != null) {
                int nonEmpty = 0;
                for (int i = 0; i < chunk.getSections().length; i++) {
                    if (chunk.getSections()[i] != null && !chunk.getSections()[i].hasOnlyAir()) {
                        nonEmpty++;
                    }
                }
                sb.append(" sections=").append(nonEmpty);
                sb.append(" surfWG=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8));
                sb.append(" floorWG=").append(chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 8, 8));
                sb.append(" b(8,60,8)=").append(chunk.getBlockState(
                    new net.minecraft.core.BlockPos(cx * 16 + 8, 60, cz * 16 + 8)));
                sb.append(" b(8,64,8)=").append(chunk.getBlockState(
                    new net.minecraft.core.BlockPos(cx * 16 + 8, 64, cz * 16 + 8)));
                sb.append(" b(8,100,8)=").append(chunk.getBlockState(
                    new net.minecraft.core.BlockPos(cx * 16 + 8, 100, cz * 16 + 8)));
                sb.append(" biome=").append(chunk.getNoiseBiome(8, 60, 8).unwrapKey()
                    .map(Object::toString).orElse("?"));
            }
            System.out.println(sb);
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] gen FAILED: " + t);
            t.printStackTrace(System.out);
        }
        System.out.flush();
    }
}
