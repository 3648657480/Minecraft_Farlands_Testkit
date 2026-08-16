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
        return (int)(long)Math.floor(((Entity)(Object)this).getX());
    }

    @Overwrite
    public int getBlockY() {
        return (int)(long)Math.floor(((Entity)(Object)this).getY());
    }

    @Overwrite
    public int getBlockZ() {
        return (int)(long)Math.floor(((Entity)(Object)this).getZ());
    }
}
