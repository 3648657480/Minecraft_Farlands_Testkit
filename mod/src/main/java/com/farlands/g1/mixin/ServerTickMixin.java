package com.farlands.g1.mixin;

import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerTickMixin {

    @Inject(method = "shouldTickBlocksAt", at = @At("HEAD"), cancellable = true)
    private void farlands$forceTick(long packedPos, CallbackInfoReturnable<Boolean> cir) {
        int cx = (int)(packedPos >> 32);
        int cz = (int)packedPos;
        if (Math.abs(cx) > 1875000 || Math.abs(cz) > 1875000) {
            cir.setReturnValue(true);
        }
    }
}
