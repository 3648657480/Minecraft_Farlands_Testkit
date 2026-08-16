package com.farlands.g1.mixin;

import com.farlands.g1.util.FloatingOrigin;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Frustum.class)
public class FrustumMixin {
    @Unique private static int bbHits;

    @Unique
    private static boolean isFar(AABB a) {
        return FloatingOrigin.isActive();
    }

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    public void bypassVisible(AABB aabb, CallbackInfoReturnable<Boolean> cir) {
        if (isFar(aabb)) cir.setReturnValue(true);
    }

    @Inject(method = "cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I",
            at = @At("HEAD"), cancellable = true)
    private void bypassCubeBB(BoundingBox bb, CallbackInfoReturnable<Integer> cir) {
        if (Math.abs(bb.minX()) > 30000000 || Math.abs(bb.maxX()) > 30000000
            || Math.abs(bb.minZ()) > 30000000 || Math.abs(bb.maxZ()) > 30000000) {
            ++bbHits;
            if (bbHits % 200 == 0) System.out.println("[D1] FrustumBB bypass hits=" + bbHits);
            cir.setReturnValue(-2);
        }
    }

    @Inject(method = "cubeInFrustum(DDDDDD)I", at = @At("HEAD"), cancellable = true)
    private void bypassCubeD(double x1, double y1, double z1, double x2, double y2, double z2, CallbackInfoReturnable<Integer> cir) {
        if (Math.abs(x1) > 3.0E7F || Math.abs(z1) > 3.0E7F
            || Math.abs(x2) > 3.0E7F || Math.abs(z2) > 3.0E7F) {
            cir.setReturnValue(-2);
        }
    }

    @Inject(method = "pointInFrustum(DDD)Z", at = @At("HEAD"), cancellable = true)
    private void bypassPoint(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Math.abs(x) > 3.0E7F || Math.abs(z) > 3.0E7F) cir.setReturnValue(true);
    }
}
