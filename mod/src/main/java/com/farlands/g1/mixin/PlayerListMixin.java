package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.LevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E line: the saved player position (from an old world or a pre-epoch
 * session) lives outside the local domain and makes the player spawn with
 * a huge AABB - collisions stop working entirely while block interaction
 * (which goes through the patched ray path) still works. Under an active
 * epoch the player enters at the local spawn; the saved position is
 * overwritten on the next save.
 */
@Mixin(PlayerList.class)
public class PlayerListMixin {

    @Inject(method = "placeNewPlayer", at = @At("TAIL"))
    private void farlands$resetPosition(Connection connection, ServerPlayer player,
            CommonListenerCookie cookie, CallbackInfo ci) {
        if (!FarProjection.isEpochActive()) {
            return;
        }
        LevelData.RespawnData rd = player.level().getServer()
            .getWorldData().overworldData().getRespawnData();
        BlockPos pos = rd.pos();
        player.teleportTo((double) pos.getX() + 0.5, (double) pos.getY(), (double) pos.getZ() + 0.5);
        System.out.println("[FarLands] player position reset to local spawn ("
            + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
        System.out.flush();
    }
}
