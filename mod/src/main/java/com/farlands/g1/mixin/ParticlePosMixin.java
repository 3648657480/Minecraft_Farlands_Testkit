package com.farlands.g1.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SingleQuadParticle.class)
public class ParticlePosMixin {

    @Unique
    private static double farlands$wrap(double v) {
        double floor = Math.floor(v);
        return (double)(int)(long)floor + (v - floor);
    }

    @Redirect(method = "extractRotatedQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;position()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 farlands$wrapCameraPos(Camera camera) {
        Vec3 p = camera.position();
        return new Vec3(farlands$wrap(p.x), p.y, farlands$wrap(p.z));
    }
}
