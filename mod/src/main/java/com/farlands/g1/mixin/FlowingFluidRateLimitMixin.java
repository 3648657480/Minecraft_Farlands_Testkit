package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * E line: fluid tick rate limit.
 *
 * <p>Every fluid block tick runs a recursive slope search (getSlopeDistance,
 * up to 3^depth block lookups). In anomalous terrain (e.g. the water-column
 * world at extreme coordinates) the number of ticking fluid blocks explodes
 * and the server thread burns CPU in RUNNABLE forever (observed via the
 * watchdog: FlowingFluid.getSlopeDistance recursion chain). Cap the number
 * of fluid ticks per game tick; the rest run on later ticks.</p>
 */
@Mixin(FlowingFluid.class)
public class FlowingFluidRateLimitMixin {

    @Unique
    private static final AtomicInteger farlands$count = new AtomicInteger();
    @Unique
    private static volatile long farlands$lastTick = -1;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void farlands$rateLimit(ServerLevel level, BlockPos pos, BlockState state, FluidState fluidState,
            CallbackInfo ci) {
        if (!FarProjection.isEpochActive()) {
            return;
        }
        long now = level.getGameTime();
        if (now != farlands$lastTick) {
            farlands$lastTick = now;
            farlands$count.set(0);
        }
        int max = 2000;
        try {
            max = Integer.parseInt(System.getProperty("farlands.fluid_tick_limit", "2000"));
        } catch (NumberFormatException ignored) {
        }
        if (farlands$count.incrementAndGet() > max) {
            ci.cancel();
        }
    }
}
