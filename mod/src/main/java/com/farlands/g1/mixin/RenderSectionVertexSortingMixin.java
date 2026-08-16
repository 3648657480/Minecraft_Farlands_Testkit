package com.farlands.g1.mixin;

import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.mojang.blaze3d.vertex.VertexSorting;

@Mixin(targets = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection")
public abstract class RenderSectionVertexSortingMixin {

    @Overwrite
    private VertexSorting createVertexSorting(SectionPos sectionPos, Vec3 cameraPos) {
        long originX = (long)sectionPos.x() * 16L;
        long originY = (long)sectionPos.y() * 16L;
        long originZ = (long)sectionPos.z() * 16L;
        float dx = (float)(cameraPos.x - (double)originX);
        float dy = (float)(cameraPos.y - (double)originY);
        float dz = (float)(cameraPos.z - (double)originZ);
        return VertexSorting.byDistance(dx, dy, dz);
    }
}
