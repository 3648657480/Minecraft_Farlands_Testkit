package com.farlands.g1.mixin;

import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySectionStorage.class)
public class EntitySectionStorageMixin {
    @Inject(method = "forEachAccessibleNonEmptySection",
        at = @At("HEAD"), cancellable = true)
    private void guardForEach(AABB aabb, AbortableIterationConsumer<EntitySection<?>> consumer, CallbackInfo ci) {
        if (Math.abs(aabb.minX) > (double)3.3554E7F || Math.abs(aabb.minZ) > (double)3.3554E7F) {
            ci.cancel();
        }
    }

    @Inject(method = "getExistingSectionPositionsInChunk",
        at = @At("HEAD"), cancellable = true)
    private void guardChunk(long chunkKey, CallbackInfoReturnable<LongStream> cir) {
        int cx = (int)chunkKey;
        int cz = (int)(chunkKey >> 32);
        if (Math.abs(cx) >= 2097000 || Math.abs(cz) >= 2097000) {
            cir.setReturnValue(LongStream.empty());
        }
    }

    @Inject(method = "getExistingSectionsInChunk",
        at = @At("HEAD"), cancellable = true)
    private void guardChunkStream(long chunkKey, CallbackInfoReturnable<Stream<EntitySection<?>>> cir) {
        int cx = (int)chunkKey;
        int cz = (int)(chunkKey >> 32);
        if (Math.abs(cx) >= 2097000 || Math.abs(cz) >= 2097000) {
            cir.setReturnValue(Stream.empty());
        }
    }
}
