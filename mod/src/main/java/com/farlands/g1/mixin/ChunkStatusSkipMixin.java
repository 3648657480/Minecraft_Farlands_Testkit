package com.farlands.g1.mixin;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.SectionPos;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.Util;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkStatusTasks.class)
public class ChunkStatusSkipMixin {

    private static boolean extreme(ChunkAccess chunk) {
        return Math.abs(chunk.getPos().x()) > 134000000 || Math.abs(chunk.getPos().z()) > 134000000;
    }

    @Inject(method = "generateFeatures", at = @At("HEAD"), cancellable = true)
    private static void farlands$features(WorldGenContext ctx, ChunkStep step, StaticCache2D cache,
            ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (extreme(chunk)) cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }

    @Inject(method = "light", at = @At("HEAD"), cancellable = true)
    private static void farlands$light(WorldGenContext ctx, ChunkStep step, StaticCache2D cache,
            ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (extreme(chunk)) cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }

    @Inject(method = "initializeLight", at = @At("HEAD"), cancellable = true)
    private static void farlands$initLight(WorldGenContext ctx, ChunkStep step, StaticCache2D cache,
            ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (extreme(chunk)) {
            cir.setReturnValue(CompletableFuture.completedFuture(chunk));
        }
    }

    @Inject(method = "generateSpawn", at = @At("HEAD"), cancellable = true)
    private static void farlands$spawn(WorldGenContext ctx, ChunkStep step, StaticCache2D cache,
            ChunkAccess chunk, CallbackInfoReturnable<CompletableFuture<ChunkAccess>> cir) {
        if (extreme(chunk)) cir.setReturnValue(CompletableFuture.completedFuture(chunk));
    }
}
