package com.farlands.g1.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards the fluid interaction at the +/-2^31 extreme.
 *
 * <p>Beyond the int boundary the interaction box straddles block
 * coordinates that wrap to the opposite side of the world, and the
 * per-block lookups become pathologically expensive - the render thread
 * froze for minutes inside {@code EntityFluidInteraction.update} with a
 * normal 0.6-wide box. Bisected empirically: skipping {@code update}
 * entirely at far coordinates restores full playability. Fluid physics
 * (swimming/lava) is simply disabled beyond +/-2e9 blocks until the
 * underlying lookup is fixed (tracked in ROADMAP).</p>
 */
@Mixin(EntityFluidInteraction.class)
public abstract class EntityFluidInteractionFarGuardMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void farlands$guardExtreme(Entity entity, boolean ignoreCurrent, CallbackInfo ci) {
        double x = entity.getX();
        double z = entity.getZ();
        if (x > 2_000_000_000.0 || x < -2_000_000_000.0
            || z > 2_000_000_000.0 || z < -2_000_000_000.0) {
            ci.cancel();
        }
    }
}
