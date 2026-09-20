package com.farlands.g1.mixin;

import com.farlands.g1.util.FarProjection;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

/**
 * E line: {@code /realtp} - teleport using REAL coordinates, parsed as
 * arbitrary-precision numbers (supports scientific notation and values
 * beyond double's range, e.g. 1e1000).
 *
 * <pre>
 *   /realtp &lt;x&gt; &lt;y&gt; &lt;z&gt;                 teleport yourself
 *   /realtp &lt;targets&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt;       teleport entities (@p/@e/...)
 * </pre>
 *
 * Out-of-window targets trigger the archive relocation with an exact
 * BigInteger epoch.
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
                    .then(Commands.argument("x", StringArgumentType.word())
                        .then(Commands.argument("y", StringArgumentType.word())
                            .then(Commands.argument("z", StringArgumentType.word())
                                .executes(ctx -> farlands$run(
                                    EntityArgument.getEntities(ctx, "targets"),
                                    StringArgumentType.getString(ctx, "x"),
                                    StringArgumentType.getString(ctx, "y"),
                                    StringArgumentType.getString(ctx, "z"),
                                    ctx.getSource()))))))
                .then(Commands.argument("x", StringArgumentType.word())
                    .then(Commands.argument("y", StringArgumentType.word())
                        .then(Commands.argument("z", StringArgumentType.word())
                            .executes(ctx -> farlands$run(
                                List.of(ctx.getSource().getEntityOrException()),
                                StringArgumentType.getString(ctx, "x"),
                                StringArgumentType.getString(ctx, "y"),
                                StringArgumentType.getString(ctx, "z"),
                                ctx.getSource()))))));
    }

    private static int farlands$run(Collection<? extends Entity> targets, String xs, String ys, String zs,
            CommandSourceStack source) {
        BigInteger realX;
        BigInteger realZ;
        int realY;
        try {
            realX = new BigDecimal(xs).toBigInteger();
            realZ = new BigDecimal(zs).toBigInteger();
            realY = new BigDecimal(ys).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            source.sendFailure(Component.literal("坐标解析失败: " + e.getMessage()));
            return 0;
        }
        BigInteger epochX = FarProjection.epochBigX();
        BigInteger epochZ = FarProjection.epochBigZ();
        BigInteger localX = realX.subtract(epochX);
        BigInteger localZ = realZ.subtract(epochZ);
        BigInteger limit = BigInteger.valueOf(2_147_483_647L - 100_000L);
        if (FarProjection.isEpochActive()
            && (localX.abs().compareTo(limit) > 0 || localZ.abs().compareTo(limit) > 0)) {
            BigInteger newEpochX = realX.divide(BigInteger.valueOf(16)).multiply(BigInteger.valueOf(16));
            BigInteger newEpochZ = realZ.divide(BigInteger.valueOf(16)).multiply(BigInteger.valueOf(16));
            BigInteger shiftChunks = epochX.subtract(newEpochX).divide(BigInteger.valueOf(16));
            com.farlands.g1.FarRelocate.pending = new com.farlands.g1.FarRelocate.Request(
                0, 0, newEpochX, newEpochZ, true);
            source.sendSuccess(() -> Component.literal(
                "目标超出当前窗口，正在重定位世界（新 epoch 约 " + abbreviate(newEpochX) + "，平移 "
                + abbreviate(shiftChunks) + " chunks）..."), false);
            System.out.println("[FarLands] /realtp out-of-window -> relocate: newEpoch=("
                + newEpochX + "," + newEpochZ + ") shiftChunks=" + shiftChunks + " targets=" + targets.size());
            System.out.flush();
            return 1;
        }
        double lx = localX.doubleValue();
        double lz = localZ.doubleValue();
        int count = 0;
        for (Entity e : targets) {
            if (e instanceof ServerPlayer sp) {
                sp.teleportTo(lx, realY, lz);
            } else {
                e.absSnapTo(lx, realY, lz);
            }
            count++;
        }
        System.out.println("[FarLands] /realtp real=(" + realX + "," + realY + "," + realZ
            + ") -> local=(" + lx + "," + realY + "," + lz + ") targets=" + count);
        System.out.flush();
        return count;
    }

    private static String abbreviate(BigInteger v) {
        String s = v.toString();
        if (s.length() <= 24) {
            return s;
        }
        String sign = s.startsWith("-") ? "-" : "";
        String digits = sign.isEmpty() ? s : s.substring(1);
        return sign + digits.charAt(0) + "." + digits.substring(1, Math.min(10, digits.length()))
            + "e+" + (digits.length() - 1);
    }
}
