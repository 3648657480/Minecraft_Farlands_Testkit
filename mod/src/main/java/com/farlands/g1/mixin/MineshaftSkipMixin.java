package com.farlands.g1.mixin;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips mineshaft generation entirely.
 *
 * <p>Mineshaft piece placement overflows 32-bit arithmetic beyond
 * +/-1073741824 and can hang or crash chunk generation in the far domain.
 * Mineshafts are purely cosmetic, so they are disabled everywhere.</p>
 */
@Mixin(MineshaftStructure.class)
public class MineshaftSkipMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void farlands$skip(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        cir.setReturnValue(Optional.empty());
    }
}
