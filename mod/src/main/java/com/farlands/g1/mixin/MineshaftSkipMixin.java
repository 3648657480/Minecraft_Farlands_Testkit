package com.farlands.g1.mixin;

import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Skips mineshaft generation beyond the far band only.
 *
 * <p>Mineshaft piece placement overflows 32-bit arithmetic beyond
 * +/-1073741824 and can hang or crash chunk generation in the far domain.
 * Mineshafts remain fully enabled in the normal play area.</p>
 */
@Mixin(MineshaftStructure.class)
public class MineshaftSkipMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void farlands$skip(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        ChunkPos pos = context.chunkPos();
        if (Math.abs(pos.x()) > 134000000 || Math.abs(pos.z()) > 134000000) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
