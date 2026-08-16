package com.farlands.g1.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public class LevelRendererCamWrapMixin {

    @Unique
    private static double farlands$wrap(double v) {
        double floor = Math.floor(v);
        return (double)(int)(long)floor + (v - floor);
    }

    @ModifyVariable(method = "submitBlockOutline", at = @At("STORE"), ordinal = 0)
    private Vec3 farlands$wrapOutlineCam(Vec3 cameraPos) {
        return new Vec3(farlands$wrap(cameraPos.x), farlands$wrap(cameraPos.y), farlands$wrap(cameraPos.z));
    }

    @ModifyVariable(method = "submitBlockEntities", at = @At("STORE"), ordinal = 0)
    private double farlands$wrapBlockCamX(double camX) {
        return farlands$wrap(camX);
    }

    @ModifyVariable(method = "submitBlockEntities", at = @At("STORE"), ordinal = 1)
    private double farlands$wrapBlockCamY(double camY) {
        return farlands$wrap(camY);
    }

    @ModifyVariable(method = "submitBlockEntities", at = @At("STORE"), ordinal = 2)
    private double farlands$wrapBlockCamZ(double camZ) {
        return farlands$wrap(camZ);
    }

    @ModifyVariable(method = "submitBlockDestroyAnimation", at = @At("STORE"), ordinal = 0)
    private double farlands$wrapDestroyCamX(double camX) {
        return farlands$wrap(camX);
    }

    @ModifyVariable(method = "submitBlockDestroyAnimation", at = @At("STORE"), ordinal = 1)
    private double farlands$wrapDestroyCamY(double camY) {
        return farlands$wrap(camY);
    }

    @ModifyVariable(method = "submitBlockDestroyAnimation", at = @At("STORE"), ordinal = 2)
    private double farlands$wrapDestroyCamZ(double camZ) {
        return farlands$wrap(camZ);
    }
}
