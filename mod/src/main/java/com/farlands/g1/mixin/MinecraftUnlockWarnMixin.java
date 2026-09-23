package com.farlands.g1.mixin;

import com.farlands.g1.client.UnlockWarn;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client startup hook for the UNLOCK lab tool warning (Vulkan gate). No-op
 * unless the unlock flags are set; see docs/UNLOCK-DESIGN.md.
 */
@Mixin(Minecraft.class)
public class MinecraftUnlockWarnMixin {

    @Inject(method = "run", at = @At("HEAD"))
    private void farlands$unlockWarn(CallbackInfo ci) {
        UnlockWarn.check((Minecraft) (Object) this);
    }
}
