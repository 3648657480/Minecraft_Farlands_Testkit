package com.farlands.g1.mixin;

import com.farlands.g1.util.FarLandsEpoch;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public class ChunkMapEpochMixin {
    @Inject(method = "applyStep", at = @At("HEAD"))
    private void farlands$setEpoch(GenerationChunkHolder holder, ChunkStep step, StaticCache2D cache,
            CallbackInfoReturnable<Boolean> cir) {
        int cx = holder.getPos().x();
        int cz = holder.getPos().z();
        if (Math.abs(cx) > 2097000 || Math.abs(cz) > 2097000) {
            FarLandsEpoch.centerX = cx;
            FarLandsEpoch.centerZ = cz;
            FarLandsEpoch.ready = true;
        }
    }
}
