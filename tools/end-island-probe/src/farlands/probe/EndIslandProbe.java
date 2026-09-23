package farlands.probe;

import com.farlands.g1.runtime.EndIslandMath;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * B-line numeric probe for the End island density function.
 *
 * <p>Validates two properties of the real-coordinate patch directly, without
 * the game loop:</p>
 * <ol>
 *   <li><b>normal range</b>: {@link EndIslandMath#heightValue} is bit-identical
 *       to the vanilla {@code int} reference for every section coordinate
 *       vanilla can produce ({@code |section| <= 2^28}); this includes the
 *       vanilla {@code int}-square overflow region ({@code section > ~32768})
 *       that the End world border (30M blocks) reaches.</li>
 *   <li><b>far range</b>: beyond {@code 2^28} the wide path runs and no longer
 *       repeats when the real section advances by 2^29 (= 2^32 blocks / 8),
 *       while the local-int reference stays constant (2^32 period).</li>
 * </ol>
 *
 * <p>Run: gradlew :mod:endIslandProbe</p>
 */
public final class EndIslandProbe {

    private static final long SECTION_MAX = 1L << 28; // vanilla-reachable |section| max

    public static void main(String[] args) {
        for (long seed : new long[] {0L, 12345L, -7820689566140440746L}) {
            SimplexNoise noise = makeNoise(seed);
            System.out.println("==== seed " + seed + " ====");
            normalRange(noise);
            boundary(noise);
            farPeriod(noise);
            System.out.println();
        }
    }

    private static SimplexNoise makeNoise(long seed) {
        RandomSource r = new LegacyRandomSource(seed);
        r.consumeCount(17292);
        return new SimplexNoise(r);
    }

    /** All section coordinates vanilla can reach: |blockX| <= 2^31-1 => |section| <= 2^28. */
    private static void normalRange(SimplexNoise noise) {
        int mismatches = 0;
        int checked = 0;
        // dense small bank
        for (int x = -4000; x <= 4000; x += 1) {
            for (int z = -4000; z <= 4000; z += 977) {
                checked += check(noise, x, z);
                mismatches += lastMismatch;
            }
        }
        // large bank across the whole vanilla range (including int-overflow region)
        long[] samples = {40000, 100000, 262144, 1000000, 3750000, 10000000,
            SECTION_MAX - 1, SECTION_MAX};
        for (long s : samples) {
            checked += check(noise, (int) s, (int) s);
            mismatches += lastMismatch;
            checked += check(noise, (int) s, -(int) s);
            mismatches += lastMismatch;
            checked += check(noise, (int) s, 0);
            mismatches += lastMismatch;
        }
        System.out.println("normal range: checked=" + checked + " mismatches=" + mismatches
            + (mismatches == 0 ? "  -> BIT-IDENTICAL over all vanilla-reachable sections" : "  -> DIFFERENT"));
    }

    private static int lastMismatch;

    private static int check(SimplexNoise noise, int x, int z) {
        lastMismatch = 0;
        float wide = EndIslandMath.heightValue(noise, x, z);
        float ref = EndIslandMath.vanillaHeightValue(noise, x, z);
        if (Float.floatToIntBits(wide) != Float.floatToIntBits(ref)) {
            lastMismatch = 1;
            System.out.println("  MISMATCH at (" + x + "," + z + "): dispatcher=" + wide + " vanilla=" + ref);
        }
        return 1;
    }

    /** The dispatcher must keep matching vanilla at the reachable edge and go wide past it. */
    private static void boundary(SimplexNoise noise) {
        int edge = (int) SECTION_MAX;
        float w1 = EndIslandMath.heightValue(noise, edge, edge);
        float r1 = EndIslandMath.vanillaHeightValue(noise, edge, edge);
        float w2 = EndIslandMath.heightValue(noise, edge + 1, edge + 1);
        float wid2 = EndIslandMath.wideHeightValue(noise, edge + 1, edge + 1);
        boolean atEdgeUsesVanilla = Float.floatToIntBits(w1) == Float.floatToIntBits(r1);
        boolean pastEdgeUsesWide = Float.floatToIntBits(w2) == Float.floatToIntBits(wid2);
        System.out.println("boundary: s=2^28 dispatcher==vanilla-int=" + atEdgeUsesVanilla
            + " ; s=2^28+1 dispatcher==wide=" + pastEdgeUsesWide + " (path switch at the reachable edge)");
    }

    /** Real-coordinate non-periodicity: section advances by 2^29 when block advances by 2^32. */
    private static void farPeriod(SimplexNoise noise) {
        long e1 = 1L << 32;
        long e2 = 1L << 33; // e1 + 2^32
        // local block offset 8: realSection = (epoch + 8)/8, matching the noise pipeline's cell sample
        double s1 = (e1 + 8) / 8.0;
        double s2 = (e2 + 8) / 8.0;
        float wide1 = EndIslandMath.heightValue(noise, s1, s1);
        float wide2 = EndIslandMath.heightValue(noise, s2, s2);
        // local-int view: vanilla (unpatched) sees the LOCAL section, identical for both epochs
        float local = EndIslandMath.vanillaHeightValue(noise, 1, 1);
        System.out.println("far periodicity: realSection s(E=2^32)=" + s1 + " s(E=2^33)=" + s2);
        System.out.println("  patched  E=2^32 -> " + wide1 + " ; E=2^33 -> " + wide2 + " ; equal="
            + (Float.floatToIntBits(wide1) == Float.floatToIntBits(wide2)));
        System.out.println("  local    E=2^32 -> " + local + " ; E=2^33 -> " + local + " ; equal=true (2^32 period)");
    }
}
