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

    /** True when the epoch origin is beyond exact double integers. */
    private static boolean exactMode() {
        java.math.BigInteger ex = com.farlands.g1.util.FarProjection.epochBigX();
        java.math.BigInteger ez = com.farlands.g1.util.FarProjection.epochBigZ();
        return ex.abs().bitLength() > 53 || ez.abs().bitLength() > 53;
    }

    /** XYZ line: real coordinates, exact beyond double precision. */
    public static String xyzLine(Entity entity) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        if (com.farlands.g1.util.FarProjection.isEpochActive() && exactMode()) {
            java.math.BigInteger bx = com.farlands.g1.util.FarProjection.realBlockBigX((long) Math.floor(x));
            java.math.BigInteger bz = com.farlands.g1.util.FarProjection.realBlockBigZ((long) Math.floor(z));
            return "XYZ (real): " + abbreviate(bx) + " / " + (long) Math.floor(y) + " / " + abbreviate(bz);
        }
        return String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f",
            com.farlands.g1.util.FarProjection.displayX(x), y,
            com.farlands.g1.util.FarProjection.displayZ(z));
    }

    /** Block line: real block coordinates, exact beyond double precision. */
    public static String blockLine(Entity entity) {
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        if (com.farlands.g1.util.FarProjection.isEpochActive() && exactMode()) {
            java.math.BigInteger bx = com.farlands.g1.util.FarProjection.realBlockBigX((long) Math.floor(x));
            java.math.BigInteger bz = com.farlands.g1.util.FarProjection.realBlockBigZ((long) Math.floor(z));
            return "Block (real): " + abbreviate(bx) + " / " + (long) Math.floor(y) + " / " + abbreviate(bz);
        }
        return String.format(Locale.ROOT, "Block: %.6g %.0f %.6g",
            com.farlands.g1.util.FarProjection.displayX(Math.floor(x)), y,
            com.farlands.g1.util.FarProjection.displayZ(Math.floor(z)));
    }

    /** Chunk line: real chunk coordinates, exact beyond double precision. */
    public static String chunkLine(Entity entity) {
        double x = entity.getX();
        double z = entity.getZ();
        if (com.farlands.g1.util.FarProjection.isEpochActive() && exactMode()) {
            java.math.BigInteger cx = floorDiv16(com.farlands.g1.util.FarProjection.realBlockBigX((long) Math.floor(x)));
            java.math.BigInteger cz = floorDiv16(com.farlands.g1.util.FarProjection.realBlockBigZ((long) Math.floor(z)));
            return "Chunk (real): " + abbreviate(cx) + " / " + (long) Math.floor(entity.getY()) + " / " + abbreviate(cz);
        }
        return String.format(Locale.ROOT, "Chunk: %.6g %.0f %.6g",
            com.farlands.g1.util.FarProjection.displayX(Math.floor(x)) / 16.0,
            Math.floor(entity.getY()),
            com.farlands.g1.util.FarProjection.displayZ(Math.floor(z)) / 16.0);
    }

    /** Floor division by 16 for BigInteger (no floorDiv dependency). */
    private static java.math.BigInteger floorDiv16(java.math.BigInteger v) {
        java.math.BigInteger[] qr = v.divideAndRemainder(java.math.BigInteger.valueOf(16));
        return qr[1].signum() < 0 ? qr[0].subtract(java.math.BigInteger.ONE) : qr[0];
    }

    /** Epoch lap count in 2^31-block windows (and the remainder inside the lap). */
    private static String laps(java.math.BigInteger epoch) {
        java.math.BigInteger window = java.math.BigInteger.ONE.shiftLeft(31);
        java.math.BigInteger[] qr = epoch.divideAndRemainder(window);
        return qr[0] + " (+" + qr[1] + ")";
    }

    public static List<String> extraLines(Minecraft mc, Level level, Entity entity) {
        List<String> out = new ArrayList<>();
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        if (com.farlands.g1.util.FarProjection.isEpochActive()) {
            java.math.BigInteger ex = com.farlands.g1.util.FarProjection.epochBigX();
            java.math.BigInteger ez = com.farlands.g1.util.FarProjection.epochBigZ();
            if (exactMode()) {
                out.add(String.format(Locale.ROOT, "Local (in-epoch): (%.3f, %.3f, %.3f)", x, y, z));
                out.add("Epoch: " + abbreviate(ex) + " / " + abbreviate(ez)
                    + "  Laps (2^31): " + laps(ex) + " / " + laps(ez));
                out.add(String.format(Locale.ROOT, "Real double ULP: +-%.4g  (block coords quantize to this)",
                    Math.ulp(com.farlands.g1.util.FarProjection.epochBlockX())));
            } else {
                out.add(String.format(Locale.ROOT, "Real position: (%.3f, %.3f, %.3f)   [epoch %s, %s]",
                    com.farlands.g1.util.FarProjection.realBlockX((int) Math.floor(x)), y,
                    com.farlands.g1.util.FarProjection.realBlockZ((int) Math.floor(z)), ex, ez));
            }
        }
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
                DensityFunction.FunctionContext ctx = new RealContext(bx, by, bz);
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

    /** Full exact value if short, otherwise leading digits + magnitude. */
    private static String abbreviate(java.math.BigInteger v) {
        String s = v.toString();
        if (s.length() <= 40) {
            return s;
        }
        String sign = s.startsWith("-") ? "-" : "";
        String digits = sign.isEmpty() ? s : s.substring(1);
        int exp = digits.length() - 1;
        return sign + digits.charAt(0) + "." + digits.substring(1, Math.min(18, digits.length()))
            + "e+" + exp + " (" + s.length() + " digits)";
    }
}
