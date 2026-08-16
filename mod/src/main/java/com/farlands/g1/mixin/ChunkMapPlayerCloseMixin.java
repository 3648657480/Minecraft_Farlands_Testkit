package com.farlands.g1.mixin;

import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkMap.class)
public class ChunkMapPlayerCloseMixin {

    @Redirect(method = "anyPlayerCloseEnoughTo", at = @At(value = "NEW", target = "net/minecraft/world/phys/Vec3", ordinal = 0))
    private Vec3 farlands$realTarget(Vec3i pos) {
        double x = pos.getX() < -100_000_000 ? (double)Integer.toUnsignedLong(pos.getX()) : (double)pos.getX();
        double z = pos.getZ() < -100_000_000 ? (double)Integer.toUnsignedLong(pos.getZ()) : (double)pos.getZ();
        return new Vec3(x, (double)pos.getY(), z);
    }
}
