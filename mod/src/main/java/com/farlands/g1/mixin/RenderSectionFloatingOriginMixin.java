package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection")
public abstract class RenderSectionFloatingOriginMixin {

    @Shadow private BlockPos.MutableBlockPos renderOrigin;

    @Overwrite
    public BlockPos getRenderOrigin() {
        return renderOrigin;
    }
}
