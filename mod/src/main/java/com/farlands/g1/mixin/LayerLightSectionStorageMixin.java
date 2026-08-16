package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LayerLightSectionStorage.class)
public class LayerLightSectionStorageMixin {

    @Unique
    private static boolean farlands$isFar(int v) {
        // long-based abs: Math.abs(Integer.MIN_VALUE) stays negative and
        // would silently bypass the guard. Beyond this the packed section
        // keys collide (phantom columns) and must not be touched until the
        // 128-bit light storage lands.
        return Math.abs((long) v) > 33_554_000L;
    }

    @Inject(method = "getStoredLevel", at = @At("HEAD"), cancellable = true)
    private void farlands$guardGet(long blockNode, CallbackInfoReturnable<Integer> cir) {
        if (farlands$isFar(BlockPos.getX(blockNode)) || farlands$isFar(BlockPos.getZ(blockNode))) {
            cir.setReturnValue(15);
        }
    }

    @Redirect(method = "getStoredLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/DataLayer;get(III)I"))
    private int farlands$safeGet(DataLayer layer, int x, int y, int z) {
        return layer == null ? 15 : layer.get(x, y, z);
    }

    @Inject(method = "setStoredLevel", at = @At("HEAD"), cancellable = true)
    private void farlands$guardSet(long blockNode, int level, CallbackInfo ci) {
        if (farlands$isFar(BlockPos.getX(blockNode)) || farlands$isFar(BlockPos.getZ(blockNode))) {
            ci.cancel();
        }
    }

    @Redirect(method = "setStoredLevel", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/chunk/DataLayer;set(IIII)V"))
    private void farlands$safeSet(DataLayer layer, int x, int y, int z, int value) {
        if (layer != null) {
            layer.set(x, y, z, value);
        }
    }

    @Inject(method = "updateSectionStatus(JZ)V", at = @At("HEAD"), cancellable = true)
    private void farlands$guardUpdate(long sectionNode, boolean flag, CallbackInfo ci) {
        if (Math.abs((long) SectionPos.x(sectionNode)) >= 2_095_000L
            || Math.abs((long) SectionPos.z(sectionNode)) >= 2_095_000L) {
            ci.cancel();
        }
    }
}
