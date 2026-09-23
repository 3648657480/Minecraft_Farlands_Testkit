package com.farlands.g1.mixin;

import com.farlands.g1.util.FarConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E line: {@code /farlands} - live configuration.
 *
 * <p>Solves the "farlands.properties is created on world entry, but you cannot
 * leave the world to edit it" contradiction: settings can be inspected and
 * changed from inside the world, applied immediately and persisted.</p>
 *
 * <pre>
 *   /farlands                        status line
 *   /farlands config                 list all current values
 *   /farlands config &lt;key&gt; &lt;value&gt;   set + apply live + persist
 *   /farlands reload                 re-read farlands.properties from disk
 * </pre>
 *
 * Epoch keys are refused here (changing the epoch of a loaded world desyncs
 * chunks); use {@code /realtp} instead.
 */
@Mixin(Commands.class)
public class FarlandsCommandMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void farlands$registerCommand(Commands.CommandSelection selection,
            CommandBuildContext context, CallbackInfo ci) {
        Commands self = (Commands) (Object) this;
        self.getDispatcher().register(
            Commands.literal("farlands")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(FarConfig.status()), false);
                    return 1;
                })
                .then(Commands.literal("config")
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal(FarConfig.list()), false);
                        return 1;
                    })
                    .then(Commands.argument("key", StringArgumentType.word())
                        .then(Commands.argument("value", StringArgumentType.greedyString())
                            .executes(ctx -> {
                                String result = FarConfig.set(
                                    StringArgumentType.getString(ctx, "key"),
                                    StringArgumentType.getString(ctx, "value"));
                                ctx.getSource().sendSuccess(
                                    () -> Component.literal("[FarLands] " + result), true);
                                return 1;
                            }))))
                .then(Commands.literal("reload")
                    .executes(ctx -> {
                        FarConfig.reload();
                        ctx.getSource().sendSuccess(
                            () -> Component.literal("[FarLands] config reloaded"), true);
                        return 1;
                    }))
        );
    }
}
