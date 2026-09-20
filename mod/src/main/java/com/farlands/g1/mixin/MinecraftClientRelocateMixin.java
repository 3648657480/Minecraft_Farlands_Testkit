package com.farlands.g1.mixin;

import com.farlands.g1.FarRelocate;
import farlands.translator.Main;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

/**
 * E3: consumes a pending relocation request on the client thread.
 *
 * <p>Sequence: halt the integrated server (saves + releases world files),
 * clear the client level, translate the world files offline, then open the
 * world again. The player ends up at the safe target chunk with the world
 * shifted around them, visually seamless.</p>
 */
@Mixin(Minecraft.class)
public class MinecraftClientRelocateMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void farlands$maybeRelocate(boolean advanceGameTime, CallbackInfo ci) {
        FarRelocate.Request req = FarRelocate.pending;
        if (req == null) {
            return;
        }
        FarRelocate.pending = null;
        Minecraft mc = (Minecraft) (Object) this;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null || !server.isRunning()) {
            return;
        }
        try {
            MinecraftServerAccessor acc = (MinecraftServerAccessor) server;
            String levelId = acc.farlands$storageSource().getLevelId();
            Path worldPath = acc.farlands$storageSource().getLevelDirectory().path();
            System.out.println("[FarLands] halting integrated server...");
            System.out.flush();
            server.halt(true);
            mc.clearClientLevel(new GenericMessageScreen(Component.literal("Far Lands: relocating world...")));
            System.out.println("[FarLands] translating world by (" + req.dx + "," + req.dz + ")");
            System.out.flush();
            int n = Main.translate(worldPath, req.dx, req.dz);
            System.out.println("[FarLands] translated " + n + " chunks, reloading world '" + levelId + "'");
            System.out.flush();
            if (!Double.isNaN(req.newEpochX)) {
                java.nio.file.Files.writeString(
                    worldPath.resolve("farlands_epoch.txt"),
                    req.newEpochX + "," + req.newEpochZ);
                System.out.println("[FarLands] epoch persisted: (" + req.newEpochX + "," + req.newEpochZ + ")");
                System.out.flush();
            }
            mc.createWorldOpenFlows().openWorld(levelId, () -> {});
        } catch (Throwable t) {
            System.out.println("[FarLands] relocate FAILED: " + t);
            t.printStackTrace(System.out);
            System.out.flush();
        }
    }
}
