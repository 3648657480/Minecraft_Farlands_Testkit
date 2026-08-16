package com.farlands.g1.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
   private static int renderCount;

   @Inject(method = "render", at = @At("HEAD"))
   private void farlands$logRender(CallbackInfo ci) {
      ++renderCount;
      if (renderCount <= 5 || renderCount % 60 == 0) {
         System.out.println("[D1] render #" + renderCount);
      }
   }
}
