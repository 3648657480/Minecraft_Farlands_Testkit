package com.farlands.g1.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @Inject(method = "clampHorizontal", at = @At("HEAD"), cancellable = true)
    private static void bypassClampHorizontal(double d, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(d);
    }

    @Inject(method = "clampVertical", at = @At("HEAD"), cancellable = true)
    private static void bypassClampVertical(double d, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(d);
    }
}
