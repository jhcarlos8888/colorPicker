package colorpicker.core;

/**
 * Numeric helpers that reproduce the Visual Basic .NET semantics the original
 * program relied on, so that every conversion gives exactly the same result.
 *
 * <p>VB.NET's {@code Math.Round(Double)} and its implicit Double to
 * Byte/Short/Integer conversions both use banker's rounding (round half to
 * even). Java's {@link Math#round(double)} rounds half up, so it must not be
 * used where parity with the original matters.
 */
public final class VB {

    private VB() {
    }

    /** {@code Math.Round(x)} in VB.NET: round half to even. */
    public static double round(double x) {
        return Math.rint(x);
    }

    /** {@code CInt(x)} / implicit Double to Integer conversion: round half to even. */
    public static int cint(double x) {
        return (int) Math.rint(x);
    }

    /**
     * {@code Random.Next(minValue, maxValue)}-style range but inclusive of the
     * upper bound (see README: the original excluded {@code maxValue}, so e.g.
     * 255 could never be produced).
     */
    public static int nextInclusive(java.util.Random random, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /** {@code Hex(value)} in VB.NET: upper-case hexadecimal without padding. */
    public static String hex(int value) {
        return Integer.toHexString(value).toUpperCase(java.util.Locale.ROOT);
    }

    /** Upper-case hexadecimal padded to two digits. */
    public static String hex2(int value) {
        String h = hex(value);
        return h.length() < 2 ? "0" + h : h;
    }
}
