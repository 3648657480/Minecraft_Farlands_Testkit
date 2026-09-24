package com.farlands.g1.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Diagnostic only (gated by {@code -Dfarlands.diag.exactwrap=true}): the exact
 * {@code PerlinNoise.wrap} with the floor computed without double rounding loss.
 * Comparing a generation with and without this isolates the terrain impact of
 * the wrap mis-rounding (the modern Far Lands mechanism).
 */
public final class FarWrapExact {

    private static final BigDecimal K = new BigDecimal("33554432"); // 2^25

    private FarWrapExact() {
    }

    public static double exact(double p) {
        BigDecimal bp = new BigDecimal(p);
        BigDecimal q = bp.divide(K, new MathContext(60)).add(new BigDecimal("0.5"));
        BigInteger m = q.setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        return bp.subtract(new BigDecimal(m).multiply(K)).doubleValue();
    }
}
