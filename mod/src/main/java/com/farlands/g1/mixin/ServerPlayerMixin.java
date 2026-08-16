package com.farlands.g1.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ServerPlayer.class)
public class ServerPlayerMixin {
    @Overwrite
    public void teleportTo(double x, double y, double z) {
        ServerPlayer self = (ServerPlayer)(Object)this;
        self.connection.teleport(x, y, z, self.getYRot(), self.getXRot());
    }
}
