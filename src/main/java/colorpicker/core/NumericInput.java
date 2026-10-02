package colorpicker.core;

/**
 * Validation of the numeric text boxes. The original accepted text for which
 * {@code IsNumeric(s) And isCompatibleNumeric(s)} was true, i.e. an unsigned
 * whole number without {@code . , + -}.
 */
public final class NumericInput {

    private NumericInput() {
    }

    /** Port of {@code SharedFunctions.isCompatibleNumeric}. */
    public static boolean isCompatibleNumeric(String s) {
        return !(s.contains(".") || s.contains(",") || s.contains("+") || s.contains("-"));
    }

    /**
     * Parses an unsigned whole number (surrounding blanks allowed, as
     * {@code IsNumeric} allowed them).
     *
     * @return the value, or {@code null} when the text is empty or not a valid
     *         unsigned number
     */
    public static Long parseUnsigned(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        // Leading zeros never change the value (VB accepted "0000012" as 12).
        int firstNonZero = 0;
        while (firstNonZero < t.length() - 1 && t.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        t = t.substring(firstNonZero);
        if (t.isEmpty() || !isCompatibleNumeric(t) || t.length() > 18) {
            return null;
        }
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        return Long.parseLong(t);
    }

    /**
     * @return the value if the text is a whole number in {@code [min, max]},
     *         otherwise {@code null}
     */
    public static Integer parseInRange(String s, int min, int max) {
        Long v = parseUnsigned(s);
        if (v == null || v < min || v > max) {
            return null;
        }
        return v.intValue();
    }
}
