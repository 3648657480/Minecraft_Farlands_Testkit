package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public class ServerPlayerInteractMixin {

    @Inject(method = "mayInteract", at = @At("HEAD"), cancellable = true)
    private void farlands$alwaysMayInteract(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (Math.abs(pos.getX()) > 30_000_000) {
            cir.setReturnValue(true);
        }
    }
}
