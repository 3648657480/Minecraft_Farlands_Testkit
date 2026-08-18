package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class ClientLevelMixin {

    @Unique
    private static boolean farlands$epoch() {
        return com.farlands.g1.util.FarProjection.isEpochActive();
    }

    @Unique
    private static int farlands$normX(int c) {
        return com.farlands.g1.util.FarProjection.chunkNorm(c);
    }

    @Unique
    private static int farlands$normZ(int c) {
        return com.farlands.g1.util.FarProjection.chunkNorm(c);
    }

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    private void farlands$fixGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!farlands$epoch() && Math.abs(pos.getX()) < 30_000_000) return;
        Level self = (Level)(Object)this;
        int sx = SectionPos.blockToSectionCoord(pos.getX());
        int sz = SectionPos.blockToSectionCoord(pos.getZ());
        ChunkAccess chunk = self.getChunkSource().getChunk(farlands$normX(sx), farlands$normZ(sz), ChunkStatus.FULL, false);
        if (chunk != null) {
            cir.setReturnValue(chunk.getBlockState(pos));
        }
    }

    @Inject(method = "getChunk(II)Lnet/minecraft/world/level/chunk/LevelChunk;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$fixGetChunk2(int x, int z, CallbackInfoReturnable<LevelChunk> cir) {
        farlands$recenterFromRequest(x, z);
        if (!farlands$epoch() && Math.abs(x) < 134000000 && Math.abs(z) < 134000000) return;
        Level self = (Level)(Object)this;
        ChunkAccess ca = self.getChunkSource().getChunk(farlands$normX(x), farlands$normZ(z), ChunkStatus.FULL, false);
        if (ca instanceof LevelChunk lc) cir.setReturnValue(lc);
    }

    @Inject(method = "getChunk(II)Lnet/minecraft/world/level/chunk/ChunkAccess;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$fixGetChunkCA(int x, int z, CallbackInfoReturnable<ChunkAccess> cir) {
        if (!farlands$epoch() && Math.abs(x) < 134000000 && Math.abs(z) < 134000000) return;
        Level self = (Level)(Object)this;
        ChunkAccess ca = self.getChunkSource().getChunk(farlands$normX(x), farlands$normZ(z), ChunkStatus.FULL, false);
        if (ca != null) cir.setReturnValue(ca);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
        at = @At("HEAD"), cancellable = true)
    private void farlands$fixGetChunk4(int x, int z, ChunkStatus status, boolean load,
            CallbackInfoReturnable<ChunkAccess> cir) {
        farlands$recenterFromRequest(x, z);
        if (!farlands$epoch() && Math.abs(x) < 134000000 && Math.abs(z) < 134000000) return;
        Level self = (Level)(Object)this;
        ChunkAccess chunk = self.getChunkSource().getChunk(farlands$normX(x), farlands$normZ(z), ChunkStatus.FULL, false);
        if (chunk != null) cir.setReturnValue(chunk);
    }

    /**
     * Recenters the epoch from the first far chunk request seen while the
     * epoch is inactive. Covers world loads where the player's saved
     * position is beyond +/-2^31: the join/spawn paths request the far
     * chunks before any teleport handler runs, and without the epoch the
     * generation would crash ("Requested chunk unavailable") and hang the
     * spawn search forever. Returns true if this request itself recentered
     * the epoch (its coordinates are already real and must pass through
     * untranslated).
     */
    @Unique
    private static boolean farlands$recenterFromRequest(int x, int z) {
        if (!com.farlands.g1.util.FarProjection.epochSupported()) return false;
        if (farlands$epoch()) return false;
        if (Math.abs(x) > 134000000 || Math.abs(z) > 134000000) {
            com.farlands.g1.util.FarProjection.setEpoch((long) x << 4, (long) z << 4);
            return true;
        }
        return false;
    }
}
