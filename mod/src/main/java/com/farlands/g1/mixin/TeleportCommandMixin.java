package com.farlands.g1.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.TeleportCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TeleportCommand.class)
public class TeleportCommandMixin {
    @Redirect(
        method = "performTeleport",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isInSpawnableBounds(Lnet/minecraft/core/BlockPos;)Z")
    )
    private static boolean alwaysTrue(BlockPos pos) {
        return true;
    }
}
