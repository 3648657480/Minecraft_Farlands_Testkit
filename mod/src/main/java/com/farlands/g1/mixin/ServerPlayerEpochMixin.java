package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * E line: re-centers the epoch origin to the teleport destination on the
 * SERVER side, before any chunk generation for the new location starts.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEpochMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
        at = @At("HEAD"))
    private void farlands$recenterEpoch(net.minecraft.server.level.ServerLevel level, double x, double y, double z,
            java.util.Set<net.minecraft.world.entity.Relative> flags, float yaw, float pitch, boolean alive,
            CallbackInfoReturnable<Boolean> cir) {
        FarProjection.setEpoch((long) Math.floor(x / 16.0) << 4, (long) Math.floor(z / 16.0) << 4);
    }
}
