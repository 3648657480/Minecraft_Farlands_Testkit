package com.farlands.g1.mixin;

import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {
   @Redirect(
      method = "updateLeaves",
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;fill(III)V"
)
   )
   private static void farlands$safeFill(DiscreteVoxelShape shape, int x, int y, int z) {
      if (x >= 0 && y >= 0 && z >= 0) {
         try {
            shape.fill(x, y, z);
         } catch (IndexOutOfBoundsException var5) {
         }

      }
   }

   @Redirect(
      method = "updateLeaves",
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;isFull(III)Z"
)
   )
   private static boolean farlands$safeIsFull(DiscreteVoxelShape shape, int x, int y, int z) {
      if (x >= 0 && y >= 0 && z >= 0) {
         try {
            return shape.isFull(x, y, z);
         } catch (IndexOutOfBoundsException var5) {
            return true;
         }
      } else {
         return true;
      }
   }
}

