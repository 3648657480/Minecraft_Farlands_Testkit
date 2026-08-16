package com.farlands.g1.mixin;

import com.farlands.g1.util.FarLandsEpoch;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ViewArea.class)
public class ViewAreaMixin {
    @Inject(method = "repositionCamera(Lnet/minecraft/core/SectionPos;)Z", at = @At("HEAD"))
    private void farlands$captureCenter(SectionPos cam, CallbackInfoReturnable<Boolean> cir) {
        int cx = cam.x();
        int cz = cam.z();
        if (Math.abs(cx) > 2097000 || Math.abs(cz) > 2097000) {
            FarLandsEpoch.centerX = cx;
            FarLandsEpoch.centerZ = cz;
            FarLandsEpoch.ready = true;
        } else {
            FarLandsEpoch.ready = false;
        }
    }
}
