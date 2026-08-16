package com.farlands.g1.mixin;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderSectionRegion.class)
public class RenderSectionRegionMixin {
   @Unique
   private static int guardCount;

   @Inject(
      method = "index(IIIIII)I",
      at = @At("RETURN"),
      cancellable = true
   )
   private static void farlands$guardIndex(int minX, int minY, int minZ, int sx, int sy, int sz, CallbackInfoReturnable<Integer> cir) {
      int idx = (Integer)cir.getReturnValue();
      if (idx < 0 || idx >= 27) {
         cir.setReturnValue(0);
      }

   }
}
