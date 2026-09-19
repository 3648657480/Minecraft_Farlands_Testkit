package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * E line: under an active epoch the saved player position (from an old
 * world / previous session, possibly a huge coordinate) must not drive the
 * spawn search - it sends the spawn chunk key out of the local domain
 * (observed: tens of thousands of task-neighbor acquires at -3*2^31 and a
 * 9089-chunk spawn load). Skip the saved position: the local respawn IS
 * the spawn. The full player data is still loaded normally by PlayerList.
 */
@Mixin(PrepareSpawnTask.class)
public abstract class PrepareSpawnTaskMixin {

    @Shadow
    private MinecraftServer server;

    @Redirect(method = "start",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;loadPlayerData(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;"))
    private Optional<?> farlands$ignoreSavedPosition(PlayerList instance, NameAndId nameAndId) {
        if (FarProjection.isEpochActive()) {
            return Optional.empty();
        }
        return instance.loadPlayerData(nameAndId);
    }

    /**
     * E line: with an active epoch the spawn-area generation is far slower
     * than vanilla (the epoch sampling cost), so the config task can start
     * while the spawn area is still generating - its radius-N ticket then
     * races the area generation and the cache-neighbor acquire hits
     * unregistered holders (NPE). Defer the player-spawn load until the
     * server is ready (spawn area done).
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void farlands$waitServerReady(CallbackInfoReturnable<Boolean> cir) {
        if (FarProjection.isEpochActive() && this.server != null && !this.server.isReady()) {
            cir.setReturnValue(false);
        }
    }
}
