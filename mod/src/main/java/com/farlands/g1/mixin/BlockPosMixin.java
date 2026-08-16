package com.farlands.g1.mixin;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockPos.class)
public class BlockPosMixin {

    @Shadow private static long PACKED_X_MASK;
    @Shadow private static long PACKED_Y_MASK;
    @Shadow private static long PACKED_Z_MASK;
    @Shadow public static int PACKED_HORIZONTAL_LENGTH;
    @Shadow private static int X_OFFSET;
    @Shadow private static int Z_OFFSET;

    @Unique private static final long HANDLE_FLAG = Long.MIN_VALUE;
    @Unique private static final ConcurrentHashMap<Long, int[]> REAL_COORDS = new ConcurrentHashMap<>();
    @Unique private static final AtomicLong NEXT_HANDLE = new AtomicLong(0);
    @Unique private static final int MAX_MAP_SIZE = 200000;

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/util/Mth;smallestEncompassingPowerOfTwo(I)I"))
    private static int farlands$h27(int value) {
        return Mth.smallestEncompassingPowerOfTwo(34000000);
    }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        int max = (1 << (PACKED_HORIZONTAL_LENGTH - 1)) - 1;
        int min = -(1 << (PACKED_HORIZONTAL_LENGTH - 1));
        if (x >= min && x <= max && z >= min && z <= max) {
            long node = 0L;
            node |= ((long)x & PACKED_X_MASK) << X_OFFSET;
            node |= ((long)y & PACKED_Y_MASK) << 0;
            node |= ((long)z & PACKED_Z_MASK) << Z_OFFSET;
            return node;
        }
        if (REAL_COORDS.size() < MAX_MAP_SIZE) {
            long handle = HANDLE_FLAG | NEXT_HANDLE.incrementAndGet();
            REAL_COORDS.put(handle, new int[]{x, y, z});
            return handle;
        }
        x = Mth.clamp(x, min, max);
        z = Mth.clamp(z, min, max);
        long node = 0L;
        node |= ((long)x & PACKED_X_MASK) << X_OFFSET;
        node |= ((long)y & PACKED_Y_MASK) << 0;
        node |= ((long)z & PACKED_Z_MASK) << Z_OFFSET;
        return node;
    }

    @Inject(method = "getX", at = @At("RETURN"), cancellable = true)
    private static void farlands$getX(long node, CallbackInfoReturnable<Integer> cir) {
        if ((node & HANDLE_FLAG) != 0) {
            int[] c = REAL_COORDS.get(node);
            if (c != null) cir.setReturnValue(c[0]);
        }
    }

    @Inject(method = "getY", at = @At("RETURN"), cancellable = true)
    private static void farlands$getY(long node, CallbackInfoReturnable<Integer> cir) {
        if ((node & HANDLE_FLAG) != 0) {
            int[] c = REAL_COORDS.get(node);
            if (c != null) cir.setReturnValue(c[1]);
        }
    }

    @Inject(method = "getZ", at = @At("RETURN"), cancellable = true)
    private static void farlands$getZ(long node, CallbackInfoReturnable<Integer> cir) {
        if ((node & HANDLE_FLAG) != 0) {
            int[] c = REAL_COORDS.get(node);
            if (c != null) cir.setReturnValue(c[2]);
        }
    }

    @Inject(method = "containing(DDD)Lnet/minecraft/core/BlockPos;",
        at = @At("RETURN"), cancellable = true)
    private static void farlands$clampContaining(double x, double y, double z, CallbackInfoReturnable<BlockPos> cir) {
        long lx = (long)Mth.floor(x);
        long lz = (long)Mth.floor(z);
        int sx = farlands$safeInt(lx);
        int sy = Mth.floor(y);
        int sz = farlands$safeInt(lz);
        BlockPos result = cir.getReturnValue();
        if (sx != result.getX() || sz != result.getZ())
            cir.setReturnValue(new BlockPos(sx, sy, sz));
    }

    @Unique
    private static int farlands$safeInt(long v) {
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int)v;
    }
}
