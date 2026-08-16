package com.farlands.g1.runtime;

import com.farlands.g1.util.FloatingOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

/**
 * Extra F3 (debug screen) lines appended to the position entry by the
 * patched {@code DebugEntryPosition}:
 *
 * <ul>
 *   <li>float precision (ULP) of the camera position on each axis</li>
 *   <li>the floating render origin and origin-relative camera position</li>
 *   <li>raw noise/density values at the player position (singleplayer)</li>
 * </ul>
 */
public final class F3Helper {

    private F3Helper() {
    }

    public static List<String> extraLines(Minecraft mc, Level level, Entity entity) {
        List<String> out = new ArrayList<>();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        out.add(String.format(Locale.ROOT, "Float precision (ULP): +-%.4g / +-%.4g / +-%.4g",
            (double) Math.ulp((float) x), (double) Math.ulp((float) y), (double) Math.ulp((float) z)));
        Vec3 origin = FloatingOrigin.get();
        if (origin != null && (origin.x != 0.0 || origin.y != 0.0 || origin.z != 0.0)) {
            out.add(String.format(Locale.ROOT, "Render origin: (%.0f, %.0f, %.0f)  cam-rel: (%.2f, %.2f, %.2f)",
                origin.x, origin.y, origin.z, x - origin.x, y - origin.y, z - origin.z));
        }
        if (level instanceof ServerLevel sl) {
            try {
                RandomState rs = sl.getChunkSource().randomState();
                NoiseRouter router = rs.router();
                int bx = (int) Math.floor(x);
                int by = (int) Math.floor(y);
                int bz = (int) Math.floor(z);
                DensityFunction.FunctionContext ctx = new DensityFunction.SinglePointContext(bx, by, bz);
                out.add(String.format(Locale.ROOT, "Continents %.4f  Erosion %.4f  Depth %.4f",
                    router.continents().compute(ctx), router.erosion().compute(ctx), router.depth().compute(ctx)));
                out.add(String.format(Locale.ROOT, "Temperature %.4f  Vegetation %.4f  Ridges %.4f",
                    router.temperature().compute(ctx), router.vegetation().compute(ctx), router.ridges().compute(ctx)));
                out.add(String.format(Locale.ROOT, "Final density %.4f  Prelim surface %.4f",
                    router.finalDensity().compute(ctx), router.preliminarySurfaceLevel().compute(ctx)));
                // probe: what the filler actually placed at the feet
                net.minecraft.core.BlockPos feet = new net.minecraft.core.BlockPos(bx, by, bz);
                out.add(String.format(Locale.ROOT, "Feet state: %s  above: %s",
                    sl.getBlockState(feet).getBlock(), sl.getBlockState(feet.above()).getBlock()));
            } catch (Throwable t) {
                out.add("Noise sampling unavailable: " + t.getClass().getSimpleName());
            }
        }
        return out;
    }
}
