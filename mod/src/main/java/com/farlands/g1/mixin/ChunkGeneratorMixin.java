package com.farlands.g1.mixin;

import java.util.Map;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {
   @Redirect(
      method = "createReferences",
      at = @At(
   value = "INVOKE",
   target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getAllStarts()Ljava/util/Map;"
)
   )
   private Map<?, ?> farlands$safeGetAllStarts(ChunkAccess chunk) {
      return chunk == null ? Map.of() : chunk.getAllStarts();
   }
}

