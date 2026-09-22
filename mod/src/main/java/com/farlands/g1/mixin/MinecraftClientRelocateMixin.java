package com.farlands.g1.mixin;

import com.farlands.g1.FarRelocate;
import farlands.translator.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * E3: consumes a pending relocation request on the client thread.
 *
 * <p>Sequence: halt the integrated server (saves + releases world files),
 * clear the client level, translate the world files offline, then open the
 * world again. The player ends up at the safe target chunk with the world
 * shifted around them, visually seamless.</p>
 */
@Mixin(Minecraft.class)
public class MinecraftClientRelocateMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void farlands$maybeRelocate(boolean advanceGameTime, CallbackInfo ci) {
        FarRelocate.Request req = FarRelocate.pending;
        if (req == null) {
            return;
        }
        FarRelocate.pending = null;
        Minecraft mc = (Minecraft) (Object) this;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || !server.isRunning()) {
            return;
        }
        try {
            MinecraftServerAccessor acc = (MinecraftServerAccessor) server;
            String levelId = acc.farlands$storageSource().getLevelId();
            Path worldPath = acc.farlands$storageSource().getLevelDirectory().path();
            System.out.println("[FarLands] halting integrated server...");
            System.out.flush();
            server.halt(true);
            mc.clearClientLevel(new GenericMessageScreen(Component.literal("Far Lands: relocating world...")));
            System.out.println("[FarLands] translating world by (" + req.dx + "," + req.dz + ")");
            System.out.flush();
            int n;
            if (req.archive) {
                n = archiveRelocate(worldPath, req);
                System.out.println("[FarLands] archive relocate done (" + n + " files)");
            } else if (Math.abs(req.dx) > Integer.MAX_VALUE || Math.abs(req.dz) > Integer.MAX_VALUE) {
                n = discardChunks(worldPath);
                System.out.println("[FarLands] relocate too far -> discarded " + n
                    + " region files (world regenerates at new epoch)");
            } else {
                n = Main.translate(worldPath, (int) req.dx, (int) req.dz);
            }
            System.out.println("[FarLands] translated " + n + " chunks, reloading world '" + levelId + "'");
            System.out.flush();
            if (req.newEpochBigX != null) {
                com.farlands.g1.util.FarConfig.setEpoch(req.newEpochBigX, req.newEpochBigZ);
                System.out.println("[FarLands] epoch persisted: (" + req.newEpochBigX + "," + req.newEpochBigZ + ")");
                System.out.flush();
            }
            mc.createWorldOpenFlows().openWorld(levelId, () -> {});
        } catch (Throwable t) {
            System.out.println("[FarLands] relocate FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }

    /**
     * Archive-style relocation: the current epoch's chunk files are moved to
     * {@code farlands_epochs/<epoch>/}; the target epoch's archive (if it
     * exists - the player has been there before) is moved back. The middle
     * is simply never generated. Fast (file moves only).
     */
    private static int archiveRelocate(Path worldPath, com.farlands.g1.FarRelocate.Request req) {
        Path epochsDir = worldPath.resolve(com.farlands.g1.util.FarConfig.archiveDir());
        String oldKey = epochKey(com.farlands.g1.util.FarConfig.epochBigX(), com.farlands.g1.util.FarConfig.epochBigZ());
        String newKey = epochKey(req.newEpochBigX, req.newEpochBigZ);
        int moved = 0;
        // 1) archive the current epoch
        moved += moveChunks(worldPath, epochsDir.resolve(oldKey));
        // 2) restore the target epoch if archived
        Path target = epochsDir.resolve(newKey);
        if (java.nio.file.Files.isDirectory(target)) {
            moved += moveChunksBack(target, worldPath);
            System.out.println("[FarLands] restored archived epoch " + newKey);
        }
        return moved;
    }

    private static String epochKey(java.math.BigInteger x, java.math.BigInteger z) {
        return "e_" + keyPart(x) + "_" + keyPart(z);
    }

    /** Short, unique, filename-safe epoch key part (leading digits + length). */
    private static String keyPart(java.math.BigInteger v) {
        String s = v.toString();
        String sign = s.startsWith("-") ? "n" : "p";
        String digits = sign.equals("n") ? s.substring(1) : s;
        return sign + digits.substring(0, Math.min(12, digits.length())) + "L" + digits.length();
    }

    /** moves dimensions/.../{region,entities}/*.mca into the archive dir */
    private static int moveChunks(Path worldPath, Path archiveDir) {
        int moved = 0;
        for (String dim : new String[]{"overworld", "the_nether", "the_end"}) {
            for (String kind : new String[]{"region", "entities"}) {
                Path src = worldPath.resolve("dimensions/minecraft/" + dim + "/" + kind);
                if (!java.nio.file.Files.isDirectory(src)) {
                    continue;
                }
                Path dst = archiveDir.resolve(dim + "/" + kind);
                try {
                    java.nio.file.Files.createDirectories(dst);
                    try (var files = java.nio.file.Files.list(src)) {
                        for (Path f : files.toList()) {
                            if (f.toString().endsWith(".mca")) {
                                java.nio.file.Files.move(f, dst.resolve(f.getFileName()),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                moved++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[FarLands] archive move failed: " + e);
                }
            }
        }
        return moved;
    }

    /** moves archived *.mca back into the world */
    private static int moveChunksBack(Path archiveDir, Path worldPath) {
        int moved = 0;
        for (String dim : new String[]{"overworld", "the_nether", "the_end"}) {
            for (String kind : new String[]{"region", "entities"}) {
                Path src = archiveDir.resolve(dim + "/" + kind);
                if (!java.nio.file.Files.isDirectory(src)) {
                    continue;
                }
                Path dst = worldPath.resolve("dimensions/minecraft/" + dim + "/" + kind);
                try {
                    java.nio.file.Files.createDirectories(dst);
                    try (var files = java.nio.file.Files.list(src)) {
                        for (Path f : files.toList()) {
                            if (f.toString().endsWith(".mca")) {
                                java.nio.file.Files.move(f, dst.resolve(f.getFileName()),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                moved++;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[FarLands] archive restore failed: " + e);
                }
            }
        }
        return moved;
    }

    private static int discardChunks(Path worldPath) {
        int deleted = 0;
        for (String dim : new String[]{"overworld", "the_nether", "the_end"}) {
            for (String kind : new String[]{"region", "entities"}) {
                Path dir = worldPath.resolve("dimensions/minecraft/" + dim + "/" + kind);
                if (!java.nio.file.Files.isDirectory(dir)) {
                    continue;
                }
                try (var files = java.nio.file.Files.list(dir)) {
                    for (Path f : files.toList()) {
                        if (f.toString().endsWith(".mca")) {
                            java.nio.file.Files.deleteIfExists(f);
                            deleted++;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[FarLands] discard failed for " + dir + ": " + e);
                }
            }
        }
        return deleted;
    }
}
