package com.farlands.g1.mixin;

import com.mojang.blaze3d.vertex.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BufferBuilder.class)
public class BufferBuilderGrowMixin {

    @ModifyConstant(method = "beginVertex", constant = @Constant(intValue = 16777215), require = 0)
    private int farlands$raiseLimit(int original) {
        return Integer.MAX_VALUE;
    }
}
