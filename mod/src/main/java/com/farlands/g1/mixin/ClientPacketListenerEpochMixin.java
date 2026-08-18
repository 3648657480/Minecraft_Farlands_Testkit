package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E line: re-centers the epoch origin to the player's chunk whenever the
 * server re-centers the chunk cache (world join, teleport, dimension
 * change). All int domains become relative to this origin.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerEpochMixin {

    @Inject(method = "handleSetChunkCacheCenter", at = @At("HEAD"))
    private void farlands$recenterEpoch(ClientboundSetChunkCacheCenterPacket packet, CallbackInfo ci) {
        FarProjection.setEpoch((long) packet.getX() << 4, (long) packet.getZ() << 4);
    }
}
