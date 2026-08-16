package com.farlands.g1.mixin;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {
    @Redirect(
        method = "<clinit>",
        at = @At(value = "FIELD", target = "Lnet/minecraft/core/BlockPos;PACKED_Y_LENGTH:I")
    )
    private static int farlands$fixYLength() {
        return 12;
    }
}
