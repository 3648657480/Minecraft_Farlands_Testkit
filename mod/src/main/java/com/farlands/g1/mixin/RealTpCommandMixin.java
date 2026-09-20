package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E line: {@code /realtp <x> <y> <z>} teleports using REAL coordinates -
 * the mod converts them to the local domain. The vanilla {@code /tp}
 * keeps its local semantics (used internally and by relative coordinates).
 *
 * <p>Absolute coordinates only; do not use {@code ~} with this command.</p>
 */
@Mixin(Commands.class)
public class RealTpCommandMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void farlands$registerRealTp(Commands.CommandSelection selection,
            CommandBuildContext context, CallbackInfo ci) {
        Commands self = (Commands) (Object) this;
        self.getDispatcher().register(
            Commands.literal("realtp")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                    .executes(ctx -> {
                        Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        double localX = pos.x;
                        double localZ = pos.z;
                        if (FarProjection.isEpochActive()) {
                            localX -= FarProjection.epochBlockX();
                            localZ -= FarProjection.epochBlockZ();
                        }
                        // out of the safe window (including int-overflow targets)
                        // -> relocate the epoch around the target
                        double limit = 2_147_483_647.0 - 100_000.0;
                        if (FarProjection.isEpochActive()
                            && (Math.abs(localX) > limit || Math.abs(localZ) > limit)) {
                            double newEpochX = Math.floor(pos.x / 16.0) * 16.0;
                            double newEpochZ = Math.floor(pos.z / 16.0) * 16.0;
                            long shiftChunksX = (long) ((FarProjection.epochBlockX() - newEpochX) / 16.0);
                            long shiftChunksZ = (long) ((FarProjection.epochBlockZ() - newEpochZ) / 16.0);
                            com.farlands.g1.FarRelocate.pending = new com.farlands.g1.FarRelocate.Request(
                                shiftChunksX, shiftChunksZ, newEpochX, newEpochZ);
                            ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                "目标超出当前窗口，正在重定位世界（新 epoch="
                                + (long) newEpochX + "," + (long) newEpochZ + "，平移 "
                                + shiftChunksX + " chunks）..."), false);
                            System.out.println("[FarLands] /realtp out-of-window -> relocate: shift=("
                                + shiftChunksX + "," + shiftChunksZ
                                + ") newEpoch=(" + newEpochX + "," + newEpochZ + ")");
                            System.out.flush();
                            return 1;
                        }
                        player.teleportTo(localX, pos.y, localZ);
                        System.out.println("[FarLands] /realtp real=(" + pos.x + "," + pos.y + "," + pos.z
                            + ") -> local=(" + localX + "," + pos.y + "," + localZ + ")");
                        System.out.flush();
                        return 1;
                    })));
    }
}
