package com.farlands.g1.mixin;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(
    targets = {"net/minecraft/world/level/border/WorldBorder$StaticBorderExtent"}
)
public class WorldBorderExtentMixin {
    @Overwrite(remap = false)
    public VoxelShape getCollisionShape() {
        return Shapes.empty();
    }
}
