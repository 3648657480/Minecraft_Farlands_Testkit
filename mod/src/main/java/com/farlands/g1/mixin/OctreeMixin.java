package com.farlands.g1.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = {"net/minecraft/client/renderer/Octree$Branch"})
public class OctreeMixin {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void farlands$guardDepth(CallbackInfoReturnable<Boolean> cir) {
        int d = DEPTH.get() + 1;
        DEPTH.set(d);
        if (d > 200) cir.setReturnValue(true);
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void farlands$popDepth(CallbackInfoReturnable<Boolean> cir) {
        DEPTH.set(DEPTH.get() - 1);
    }
}
