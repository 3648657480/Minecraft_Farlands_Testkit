package com.farlands.g1.mixin;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.world.level.levelgen.Aquifer$NoiseBasedAquifer")
public abstract class AquiferIndexMixin {

    @Shadow private int minGridX;
    @Shadow private int minGridY;
    @Shadow private int minGridZ;
    @Shadow private int gridSizeX;
    @Shadow private int gridSizeZ;
    @Shadow private Aquifer.FluidStatus[] aquiferCache;

    @Overwrite
    private int getIndex(int x, int y, int z) {
        long i = (long)x - (long)this.minGridX;
        long j = (long)y - (long)this.minGridY;
        long k = (long)z - (long)this.minGridZ;
        int sizeY = this.aquiferCache.length / (this.gridSizeX * this.gridSizeZ);
        i = Math.max(0, Math.min((long)this.gridSizeX - 1L, i));
        j = Math.max(0, Math.min((long)sizeY - 1L, j));
        k = Math.max(0, Math.min((long)this.gridSizeZ - 1L, k));
        return (int)((j * (long)this.gridSizeZ + k) * (long)this.gridSizeX + i);
    }
}
