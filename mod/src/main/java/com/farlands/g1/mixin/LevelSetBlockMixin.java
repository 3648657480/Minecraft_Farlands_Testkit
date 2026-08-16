package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelSetBlockMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true)
    private void farlands$fixSetBlock(BlockPos pos, BlockState newState, int flags, int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        if (Math.abs(pos.getX()) < 30_000_000) return;
        Level self = (Level)(Object)this;
        if (pos.getY() < self.getMinY() || pos.getY() >= self.getMaxY()) {
            cir.setReturnValue(false);
            return;
        }
        LevelChunk chunk = (LevelChunk) self.getChunkSource().getChunk(
            SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()), ChunkStatus.FULL, !self.isClientSide());
        if (chunk == null) {
            System.out.println("[D1] SetBlock chunk null server=" + !self.isClientSide() + " pos=" + pos);
            cir.setReturnValue(false);
            return;
        }
        BlockState old = chunk.setBlockState(pos, newState, flags);
        if (old == null) {
            cir.setReturnValue(false);
            return;
        }
        BlockState replaced = old;
        if (newState != old) {
            newState.onPlace(self, pos, replaced, false);
        }
        if (old.hasBlockEntity()) chunk.removeBlockEntity(pos);
        chunk.markUnsaved();
        if (newState != old) {
            self.setBlocksDirty(pos, old, newState);
            self.updateNeighborsAt(pos, old.getBlock());
        }
        if (!self.isClientSide()) {
            self.sendBlockUpdated(pos, old, newState, flags);
        }
        cir.setReturnValue(true);
    }
}
