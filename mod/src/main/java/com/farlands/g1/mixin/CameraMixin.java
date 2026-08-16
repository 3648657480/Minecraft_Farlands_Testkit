package com.farlands.g1.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private Entity entity;

    @Inject(method = "getCameraEntityPartialTicks", at = @At("HEAD"), cancellable = true)
    private void farlands$guardNullLevel(CallbackInfoReturnable<Float> cir) {
        if (this.entity == null || this.entity.level() == null) {
            cir.setReturnValue(1.0F);
        }
    }
}
