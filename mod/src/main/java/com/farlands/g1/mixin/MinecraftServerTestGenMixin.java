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
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (!farlands$testgenDone && self.getTickCount() >= Integer.getInteger("farlands.testgen.delay", 0)) {
            farlands$testgenDone = true;
            runTestGen(self);
        }
        if (!farlands$spawnDone) {
            farlands$spawnDone = true;
            runSpawnSet(self);
        }
        if (!farlands$spawnSearchDone) {
            farlands$spawnSearchDone = true;
            runSpawnSearch(self);
        }
        if (farlands$saveCountdown > 0) {
            farlands$saveCountdown--;
            if (farlands$saveCountdown == 0) {
                self.saveAllChunks(true, true, true);
                System.out.println("[FarLands-Test] DONE saved");
                System.out.flush();
                self.halt(false);
            }
        }
    }

    /**
     * Settle window before save: chunk status "full" does not mean the
     * post-processing / lighting pipeline has finished, so saving right after
     * generation makes byte comparisons nondeterministic. Wait this many ticks
     * first (default 200 = 10 s).
     */
    @Unique
    private static int farlands$saveCountdown = -1;

    @Unique
    private static boolean farlands$spawnSearchDone = false;

    /**
     * Headless replication of the client's prepare_spawn path: calls
     * PlayerSpawnFinder.findSpawn with the world respawn, which schedules
     * per-chunk SPAWN_SEARCH tickets and forces generation. Crashes here
     * reproduce the integrated-server world-entry failure without a client.
     */
    @Unique
    private static void runSpawnSearch(MinecraftServer self) {
        if (System.getProperty("farlands.testspawn") == null) return;
        try {
            net.minecraft.server.level.ServerLevel level = self.overworld();
            net.minecraft.core.BlockPos pos = self.getWorldData().overworldData().getRespawnData().pos();
            System.out.println("[FarLands-Test] spawn search start respawn=(" + pos.getX()
                + "," + pos.getY() + "," + pos.getZ() + ")");
            System.out.flush();
            java.util.concurrent.CompletableFuture<net.minecraft.world.phys.Vec3> f =
                net.minecraft.server.level.PlayerSpawnFinder.findSpawn(level, pos);
            f.whenComplete((v, t) -> {
                if (t != null) {
                    System.out.println("[FarLands-Test] spawn search FAILED: " + t);
                    t.printStackTrace(System.out);
                } else {
                    System.out.println("[FarLands-Test] spawn search OK -> " + v);
                    net.minecraft.world.level.ChunkPos spawnChunk =
                        net.minecraft.world.level.ChunkPos.containing(net.minecraft.core.BlockPos.containing(v));
                    System.out.println("[FarLands-Test] spawn ticket radius 3 (PrepareSpawnTask path) chunk="
                        + spawnChunk.x() + "," + spawnChunk.z());
                    level.getChunkSource().addTicketAndLoadWithRadius(
                        net.minecraft.server.level.TicketType.PLAYER_SPAWN, spawnChunk, 3)
                        .whenComplete((ignored2, t2) -> {
                            if (t2 != null) {
                                System.out.println("[FarLands-Test] spawn ticket FAILED: " + t2);
                                t2.printStackTrace(System.out);
                            } else {
                                System.out.println("[FarLands-Test] spawn ticket OK (radius 3 path)");
                            }
                            System.out.flush();
                        });
                }
                System.out.flush();
            });
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] spawn search setup FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
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
            double px = Double.parseDouble(parts[0].trim());
            int py = Integer.parseInt(parts[1].trim());
            double pz = Double.parseDouble(parts[2].trim());
            com.farlands.g1.util.FarProjection.setEpoch(px, pz);
            int localX = (int) (px - com.farlands.g1.util.FarProjection.epochBlockX());
            int localZ = (int) (pz - com.farlands.g1.util.FarProjection.epochBlockZ());
            level.setRespawnData(new net.minecraft.world.level.storage.LevelData.RespawnData(
                net.minecraft.core.GlobalPos.of(
                    net.minecraft.world.level.Level.OVERWORLD,
                    new net.minecraft.core.BlockPos(localX, py, localZ)), 0.0f, 0.0f));
            System.out.println("[FarLands-Test] spawn set: real=(" + px + "," + py + "," + pz
                + ") epoch=(" + com.farlands.g1.util.FarProjection.epochBlockX() + ","
                + com.farlands.g1.util.FarProjection.epochBlockZ() + ") local=(" + localX + "," + localZ + ")");
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
            // Determinism: random ticks use the level RNG (run-order dependent)
            // and grow kelp/vines, making chunk content vary between runs.
            level.getGameRules().set(
                net.minecraft.world.level.gamerules.GameRules.RANDOM_TICK_SPEED, 0, self);
            System.out.println("[FarLands-Test] random_tick_speed=0 (determinism)");
            System.out.flush();
            for (String entry : spec.split(";")) {
                if (entry.trim().isEmpty()) continue;
                String[] parts = entry.trim().split(",");
                int cx = Integer.parseInt(parts[0].trim());
                int cz = Integer.parseInt(parts[1].trim());
                int n = parts.length > 2 ? Integer.parseInt(parts[2].trim()) : 1;
                long t0 = System.currentTimeMillis();
                ChunkAccess chunk = null;
                for (int dx = 0; dx < n; dx++) {
                    for (int dz = 0; dz < n; dz++) {
                        chunk = level.getChunk(cx + dx, cz + dz);
                    }
                }
                long genMs = System.currentTimeMillis() - t0;
                int top = chunk != null ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) : -999;
                int nonEmpty = 0;
                if (chunk != null) {
                    for (int i = 0; i < chunk.getSections().length; i++) {
                        if (chunk.getSections()[i] != null && !chunk.getSections()[i].hasOnlyAir()) {
                            nonEmpty++;
                        }
                    }
                }
                StringBuilder sb = new StringBuilder();
                sb.append("[FarLands-Test] gen OK region (").append(cx).append(",").append(cz)
                    .append(")+").append(n).append("x").append(n).append(" last=(").append(cx + n - 1)
                    .append(",").append(cz + n - 1).append(") topY=").append(top).append(" timeMs=").append(genMs)
                    .append(" biome=").append(chunk.getNoiseBiome(8, 60, 8).unwrapKey().map(Object::toString).orElse("?"))
                    .append(" b(8,60,8)=").append(chunk.getBlockState(new net.minecraft.core.BlockPos(cx * 16 + 8, 60, cz * 16 + 8)))
                    .append(" topX8=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8))
                    .append(" topX9=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 9, 8))
                    .append(" topX12=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 12, 8))
                    .append(" topZ8=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 9))
                    .append(" sections=").append(nonEmpty)
                    .append(" surfWG=").append(chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8))
                    .append(" floorWG=").append(chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 8, 8))
                    .append(" b(8,64,8)=").append(chunk.getBlockState(
                        new net.minecraft.core.BlockPos(cx * 16 + 8, 64, cz * 16 + 8)))
                    .append(" b(8,100,8)=").append(chunk.getBlockState(
                        new net.minecraft.core.BlockPos(cx * 16 + 8, 100, cz * 16 + 8)));
                System.out.println(sb);
                System.out.flush();
            }
            if (System.getProperty("farlands.testgen.stop") != null) {
                farlands$saveCountdown = Integer.getInteger("farlands.testgen.settle", 200);
                System.out.println("[FarLands-Test] settling " + farlands$saveCountdown + " ticks before save");
                System.out.flush();
            }
        } catch (Throwable t) {
            System.out.println("[FarLands-Test] gen FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }
}
