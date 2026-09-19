package com.farlands.g1.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Block-coordinate accessors for entities.
 *
 * <p>In the local-domain world (epoch active), entity positions ARE local
 * already - the server runs entirely in the local domain, so
 * {@code getBlockX/Z} is the position itself. No epoch subtraction (that
 * would double-shift already-local positions, observed as mineshaft chests
 * querying chunks at -2^31). When the epoch is inactive, vanilla identity.
 */
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
