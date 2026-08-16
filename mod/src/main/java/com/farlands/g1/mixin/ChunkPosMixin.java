package com.farlands.g1.mixin;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ChunkPos.class)
public class ChunkPosMixin {

    @Overwrite
    public boolean isValid() {
        return true;
    }
}
