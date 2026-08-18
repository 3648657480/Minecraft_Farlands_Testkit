package com.farlands.g1.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Entity.class)
public class EntityMixin {
    @Overwrite
    public void absSnapTo(double x, double y, double z) {
        ((Entity)(Object)this).setPosRaw(x, y, z);
    }

    @Overwrite
    public int getBlockX() {
        double x = ((Entity)(Object)this).getX();
        if (com.farlands.g1.util.FarProjection.isEpochActive()) {
            return (int)(long)Math.floor(x - com.farlands.g1.util.FarProjection.epochBlockX());
        }
        return (int)(long)Math.floor(x);
    }

    @Overwrite
    public int getBlockY() {
        return (int)(long)Math.floor(((Entity)(Object)this).getY());
    }

    @Overwrite
    public int getBlockZ() {
        double z = ((Entity)(Object)this).getZ();
        if (com.farlands.g1.util.FarProjection.isEpochActive()) {
            return (int)(long)Math.floor(z - com.farlands.g1.util.FarProjection.epochBlockZ());
        }
        return (int)(long)Math.floor(z);
    }
}
