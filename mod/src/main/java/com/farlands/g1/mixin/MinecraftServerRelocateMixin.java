package com.farlands.g1.mixin;

import com.farlands.g1.FarRelocate;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * E3: watches the local player's real position on the server thread.
 * When |chunk| approaches the int boundary (2^31 blocks / 16), requests a
 * world relocation: the world is shifted so the player ends up at the safe
 * target chunk, and the integrated server is restarted around it.
 *
 * <p>Only fires for the integrated server (singleplayer); the relocate
 * itself must run on the client thread, see MinecraftClientRelocateMixin.</p>
 */
@Mixin(MinecraftServer.class)
public class MinecraftServerRelocateMixin {

    /** int overflow in block space happens at |block| = 2^31; chunk = /16. */
    private static final long CHUNK_LIMIT = (1L << 31) / 16L - 40000L;
    /** chunks moved back to: |playerChunk| &lt; 6000 after the shift. */
    private static final long TARGET_CHUNK = 5000L;
    /** minimum gap between relocations, prevents runaway loops. */
    private static final long RELOCATE_COOLDOWN_MS = 60_000L;
    private static volatile long lastRelocateAt;

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void farlands$detectBoundary(BooleanSupplier haveTime, CallbackInfo ci) {
        if (FarRelocate.pending != null) {
            return;
        }
        if (!((Object) this instanceof IntegratedServer)) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRelocateAt < RELOCATE_COOLDOWN_MS) {
            return;
        }
        MinecraftServer self = (MinecraftServer) (Object) this;
        for (ServerPlayer p : self.getPlayerList().getPlayers()) {
            long cx = (long) Math.floor(p.getX()) >> 4;
            long cz = (long) Math.floor(p.getZ()) >> 4;
            if (Math.abs(cx) > CHUNK_LIMIT || Math.abs(cz) > CHUNK_LIMIT) {
                long dx = TARGET_CHUNK - cx;
                long dz = TARGET_CHUNK - cz;
                if (dx < Integer.MIN_VALUE || dx > Integer.MAX_VALUE
                        || dz < Integer.MIN_VALUE || dz > Integer.MAX_VALUE) {
                    System.out.println("[FarLands] player at chunk (" + cx + "," + cz
                        + ") is outside the relocatable int range; cannot shift the world by ("
                        + dx + "," + dz + "). Moving back within +/-2^31 is required first.");
                    System.out.flush();
                    lastRelocateAt = now;
                    return;
                }
                FarRelocate.pending = new FarRelocate.Request((int) dx, (int) dz);
                lastRelocateAt = now;
                System.out.println("[FarLands] boundary reached at chunk (" + cx + "," + cz
                    + "), relocating world by (" + dx + "," + dz + ")");
                System.out.flush();
                return;
            }
        }
    }
}
