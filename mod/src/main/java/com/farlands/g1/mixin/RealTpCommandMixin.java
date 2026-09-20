package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;

/**
 * E line: {@code /realtp} - teleport using REAL coordinates.
 *
 * <pre>
 *   /realtp &lt;x&gt; &lt;y&gt; &lt;z&gt;                 teleport yourself
 *   /realtp &lt;targets&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;       teleport entities (@p/@e/...)
 * </pre>
 *
 * Out-of-window targets trigger the archive relocation. Vanilla {@code /tp}
 * keeps its local semantics.
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
                .then(Commands.argument("targets", EntityArgument.entities())
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> farlands$run(
                            EntityArgument.getEntities(ctx, "targets"),
                            Vec3Argument.getVec3(ctx, "pos"),
                            ctx.getSource()))))
                .then(Commands.argument("pos", Vec3Argument.vec3())
                    .executes(ctx -> farlands$run(
                        List.of(ctx.getSource().getEntityOrException()),
                        Vec3Argument.getVec3(ctx, "pos"),
                        ctx.getSource()))));
    }

    private static int farlands$run(Collection<? extends Entity> targets, Vec3 pos, CommandSourceStack source) {
        double localX = pos.x;
        double localZ = pos.z;
        if (FarProjection.isEpochActive()) {
            localX -= FarProjection.epochBlockX();
            localZ -= FarProjection.epochBlockZ();
        }
        double limit = 2_147_483_647.0 - 100_000.0;
        if (FarProjection.isEpochActive()
            && (Math.abs(localX) > limit || Math.abs(localZ) > limit)) {
            double newEpochX = Math.floor(pos.x / 16.0) * 16.0;
            double newEpochZ = Math.floor(pos.z / 16.0) * 16.0;
            long shiftChunksX = (long) ((FarProjection.epochBlockX() - newEpochX) / 16.0);
            long shiftChunksZ = (long) ((FarProjection.epochBlockZ() - newEpochZ) / 16.0);
            com.farlands.g1.FarRelocate.pending = new com.farlands.g1.FarRelocate.Request(
                shiftChunksX, shiftChunksZ, newEpochX, newEpochZ, true);
            source.sendSuccess(() -> Component.literal(
                "目标超出当前窗口，正在重定位世界（新 epoch="
                + (long) newEpochX + "," + (long) newEpochZ + "）..."), false);
            System.out.println("[FarLands] /realtp out-of-window -> relocate: shift=("
                + shiftChunksX + "," + shiftChunksZ
                + ") newEpoch=(" + newEpochX + "," + newEpochZ + ") targets=" + targets.size());
            System.out.flush();
            return 1;
        }
        int count = 0;
        for (Entity e : targets) {
            if (e instanceof ServerPlayer sp) {
                sp.teleportTo(localX, pos.y, localZ);
            } else {
                e.absSnapTo(localX, pos.y, localZ);
            }
            count++;
        }
        System.out.println("[FarLands] /realtp real=(" + pos.x + "," + pos.y + "," + pos.z
            + ") -> local=(" + localX + "," + pos.y + "," + localZ + ") targets=" + count);
        System.out.flush();
        return count;
    }
}
