package com.farlands.g1.mixin;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Far-domain block updates: send the packet directly (the vanilla chain
 * breaks on wrapped coordinates) but only to players that already track the
 * chunk, and throttled.
 *
 * <p>Without these two guards every block placement during far-domain chunk
 * generation produced a packet, flooding the client's render thread
 * (observed as a permanent freeze in {@code LevelExtractor.setBlocksDirty}).
 * Chunks are sent wholesale once generated, so updates for untracked chunks
 * are redundant and dropped.</p>
 */
@Mixin(ServerLevel.class)
public class ServerLevelSendMixin {

    @Unique
    private static final int MAX_PER_TICK = 500;

    @Unique
    private static final AtomicInteger TICK_COUNTER = new AtomicInteger();

    @Unique
    private static final AtomicLong LAST_RESET_MS = new AtomicLong();

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void farlands$fixSendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags,
            CallbackInfo ci) {
        if (Math.abs(pos.getX()) < 30_000_000) return;
        ci.cancel();

        long nowMs = System.nanoTime() / 1_000_000L;
        long last = LAST_RESET_MS.get();
        if (nowMs - last >= 50L) {
            if (LAST_RESET_MS.compareAndSet(last, nowMs)) {
                TICK_COUNTER.set(0);
            }
        }
        if (TICK_COUNTER.incrementAndGet() > MAX_PER_TICK) {
            return;
        }

        ServerLevel self = (ServerLevel) (Object) this;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX()) >> 2;
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ()) >> 2;
        ClientboundBlockUpdatePacket packet = new ClientboundBlockUpdatePacket(pos, newState);
        for (ServerPlayer player : self.players()) {
            if (self.getChunkSource().chunkMap.isChunkTracked(player, chunkX, chunkZ)) {
                player.connection.send(packet);
            }
        }
    }
}
