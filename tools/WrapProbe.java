import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

/**
 * Numerically probes PerlinNoise.wrap failure (the modern Far Lands mechanism).
 * wrap(x) = x - (double)((long)Math.floor(x / 2^25 + 0.5)) * 2^25
 * The wrap is "correct" while x/2^25 stays inside double precision; once the
 * +0.5 rounding is lost, the subtracted multiple is off by one -> the noise
 * input jumps by 2^25 blocks.
 */
public class WrapProbe {
    static final double K = 33554432.0; // 2^25

    static double wrap(double x) {
        return x - (double) ((long) Math.floor(x / K + 0.5)) * K;
    }

    static BigInteger exactMultiple(double p) {
        // floor(p / K + 0.5) with exact rational arithmetic
        BigDecimal bp = new BigDecimal(p);
        BigDecimal q = bp.divide(BigDecimal.valueOf(K), MathContext.DECIMAL128)
                        .add(new BigDecimal("0.5"));
        return q.toBigInteger();
    }

    static double wrapExact(double p) {
        BigDecimal bp = new BigDecimal(p);
        BigInteger m = exactMultiple(p);
        return bp.subtract(new BigDecimal(m).multiply(BigDecimal.valueOf(K))).doubleValue();
    }

    public static void main(String[] args) {
        double[] xs = {
            1.808764368955220466364897e24,
            1.808764368950000000099728e24,
            1.0e24, 1.2089258196146292e24 /*2^80*/, 2.4178516392292583e24 /*2^81*/,
            1.5111572745182865e23 /*2^77*/, 3.022314549036573e23 /*2^78*/,
            9.007199254740994e15 /*2^53*/,
        };
        System.out.println("x ; n=0..12 factor ; wrap ; exactWrap ; diff(=exact-wrap) ; failed?");
        for (double x : xs) {
            System.out.printf("%n=== x = %.10e ===%n", x);
            for (int n = 0; n <= 12; n++) {
                double f = Math.pow(2, -n);
                double p = x * f;
                double w = wrap(p);
                double we = wrapExact(p);
                double diff = we - w;
                boolean failed = Math.abs(diff) > K / 2.0;
                System.out.printf("  2^-%02d  p=%.6e  wrap=%.6e  exact=%.6e  diff=%.3e  %s%n",
                    n, p, w, we, diff, failed ? "FAIL" : "ok");
            }
        }
    }
}
