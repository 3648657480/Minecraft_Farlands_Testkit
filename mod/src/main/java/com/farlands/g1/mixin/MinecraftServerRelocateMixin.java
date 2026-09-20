package com.farlands.g1.mixin;

import com.farlands.g1.FarRelocate;
import com.farlands.g1.util.FarProjection;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * E line: automatic relocation (background mode).
 *
 * <p>Watches the local player's LOCAL position on the server thread. When it
 * approaches the int window edge (|local| &gt; 2^31 - margin) the world is
 * re-centered around the player: the new epoch becomes the player's current
 * position, the save is shifted, and the world reloads. The player lands at
 * local 0 of the new epoch - same real coordinates, seamless terrain.</p>
 *
 * <p>Only fires for the integrated server (singleplayer); the relocate
 * itself must run on the client thread, see MinecraftClientRelocateMixin.
 * Toggle: {@code -Dfarlands.auto_relocate=false}. Margin:
 * {@code -Dfarlands.relocate_margin=<blocks>}.</p>
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerRelocateMixin {

    /** minimum gap between relocations, prevents runaway loops. */
    private static final long RELOCATE_COOLDOWN_MS = 60_000L;
    private static volatile long lastRelocateAt;

    private static boolean autoRelocate() {
        return com.farlands.g1.util.FarConfig.autoRelocate();
    }

    private static double margin() {
        return com.farlands.g1.util.FarConfig.relocateMargin();
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void farlands$autoRelocate(BooleanSupplier haveTime, CallbackInfo ci) {
        if (FarRelocate.pending != null) {
            return;
        }
        if (!((Object) this instanceof IntegratedServer)) {
            return;
        }
        if (!FarProjection.isEpochActive() || !autoRelocate()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRelocateAt < RELOCATE_COOLDOWN_MS) {
            return;
        }
        MinecraftServer self = (MinecraftServer) (Object) this;
        double limit = 2_147_483_647.0 - margin();
        for (ServerPlayer p : self.getPlayerList().getPlayers()) {
            double x = p.getX();
            double z = p.getZ();
            if (Math.abs(x) > limit || Math.abs(z) > limit) {
                double newEpochX = Math.floor(x / 16.0) * 16.0;
                double newEpochZ = Math.floor(z / 16.0) * 16.0;
                double shiftX = (FarProjection.epochBlockX() - newEpochX) / 16.0;
                double shiftZ = (FarProjection.epochBlockZ() - newEpochZ) / 16.0;
                if (Math.abs(shiftX) > Integer.MAX_VALUE || Math.abs(shiftZ) > Integer.MAX_VALUE) {
                    System.out.println("[FarLands] auto-relocate skipped: shift too large ("
                        + (long) shiftX + "," + (long) shiftZ + ")");
                    System.out.flush();
                    lastRelocateAt = now;
                    return;
                }
                FarRelocate.pending = new FarRelocate.Request(
                    (int) shiftX, (int) shiftZ, newEpochX, newEpochZ);
                lastRelocateAt = now;
                System.out.println("[FarLands] auto-relocate: player local=(" + (long) x + "," + (long) z
                    + ") near window edge -> new epoch=(" + (long) newEpochX + "," + (long) newEpochZ
                    + ") shift=(" + (long) shiftX + "," + (long) shiftZ + ")");
                System.out.flush();
                return;
            }
        }
    }
}
