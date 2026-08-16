package com.farlands.g1.mixin;

import com.farlands.g1.util.FarLandsEpoch;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionPos.class)
public class SectionPosMixin {

    @Unique
    private static int farlands$wrap() {
        // Adaptive: widened client jars publish their wrap; vanilla layout = 2^22.
        try {
            java.lang.reflect.Field f = SectionPos.class.getDeclaredField("farlands$wrap");
            f.setAccessible(true);
            return f.getInt(null);
        } catch (ReflectiveOperationException e) {
            return 4194304;
        }
    }

    @Inject(method = "x(J)I", at = @At("RETURN"), cancellable = true)
    private static void farlands$x(long sectionNode, CallbackInfoReturnable<Integer> cir) {
        int v = cir.getReturnValue();
        int corrected = FarLandsEpoch.correctX(v);
        if (corrected != v) cir.setReturnValue(corrected);
    }

    @Inject(method = "z(J)I", at = @At("RETURN"), cancellable = true)
    private static void farlands$z(long sectionNode, CallbackInfoReturnable<Integer> cir) {
        int v = cir.getReturnValue();
        int corrected = FarLandsEpoch.correctZ(v);
        if (corrected != v) cir.setReturnValue(corrected);
    }

    @Overwrite
    public static int sectionToBlockCoord(int sectionCoord) {
        return sectionCoord * 16;
    }

    @Overwrite
    public static int sectionToBlockCoord(int sectionCoord, int offset) {
        return sectionCoord * 16 + offset;
    }

    @Overwrite
    public static int blockToSectionCoord(int blockCoord) {
        if (blockCoord > -100000000 && blockCoord < 100000000) return blockCoord >> 4;
        return Integer.divideUnsigned(blockCoord, 16);
    }

    @Overwrite
    public static int blockToSectionCoord(double coord) {
        return blockToSectionCoord((int)(long)Math.floor(coord));
    }
}
