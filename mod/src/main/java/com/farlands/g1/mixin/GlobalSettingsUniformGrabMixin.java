package com.farlands.g1.mixin;

import com.farlands.g1.util.FloatingOrigin;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlobalSettingsUniform.class)
public class GlobalSettingsUniformGrabMixin {

    @Inject(method = "update", at = @At("RETURN"))
    private void farlands$grabOrigin(int w, int h, double ga, long gt, DeltaTracker dt, int mb, Vec3 camPos, boolean rgss, CallbackInfo ci) {
        try {
            Vec3 o = (Vec3)GlobalSettingsUniform.class.getField("renderOrigin").get(null);
            if (o != null) FloatingOrigin.updateRaw(o);
        } catch (Exception ignored) {}
    }
}
