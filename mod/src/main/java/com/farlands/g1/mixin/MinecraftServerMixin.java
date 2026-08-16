package com.farlands.g1.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Overwrite
    public int getAbsoluteMaxWorldSize() {
        return Integer.MAX_VALUE;
    }
}
