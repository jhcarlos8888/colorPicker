package colorpicker.core;

import java.awt.Color;
import java.util.regex.Pattern;

/**
 * Hex / decimal / web-safe helpers (port of {@code ColorChanger} plus the
 * {@code ColorTranslator.ToHtml/FromHtml} behaviour the original used).
 */
public final class ColorChanger {

    private static final Pattern HEX_COLOR = Pattern.compile("^#([a-fA-F0-9]{6}|[a-fA-F0-9]{3})$");
    private static final int WEBSAFE_STEP = 51;

    private ColorChanger() {
    }

    /** {@code #RRGGBB} / {@code #RGB} check, identical regex to the original. */
    public static boolean isValidHexColorCode(String hexColorCode) {
        return hexColorCode != null && !hexColorCode.isEmpty() && HEX_COLOR.matcher(hexColorCode).matches();
    }

    /** {@code ColorTranslator.ToHtml(Color.FromArgb(r, g, b))}: {@code #RRGGBB} upper case. */
    public static String toHtml(int r, int g, int b) {
        return "#" + VB.hex2(r) + VB.hex2(g) + VB.hex2(b);
    }

    /** {@code #RRGGBB} upper case, alpha ignored. */
    public static String toHtml(Color c) {
        return toHtml(c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Parses {@code #RRGGBB} or {@code #RGB} (the leading {@code #} is
     * optional). Returns {@code null} when the text is not a valid hex colour.
     */
    public static Color fromHtml(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (!t.startsWith("#")) {
            t = "#" + t;
        }
        if (!isValidHexColorCode(t)) {
            return null;
        }
        String digits = expandShortHex(t.substring(1));
        return new Color(Integer.parseInt(digits, 16));
    }

    /** {@code "abc"} becomes {@code "aabbcc"}; six-digit input is returned unchanged. */
    public static String expandShortHex(String digits) {
        if (digits.length() != 3) {
            return digits;
        }
        StringBuilder sb = new StringBuilder(6);
        for (char c : digits.toCharArray()) {
            sb.append(c).append(c);
        }
        return sb.toString();
    }

    /** Hex string (with or without {@code #}) to its decimal value. */
    public static int hexToDec(String hex) {
        String digits = hex.replace("#", "");
        return Integer.parseInt(digits, 16);
    }

    public static int hexToDec(int r, int g, int b) {
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Decimal (0 - 16777215) to {@code #RRGGBB}.
     *
     * @throws IllegalArgumentException when out of range
     */
    public static String decToHex(int decValue) {
        return toHtml(decToColor(decValue));
    }

    /**
     * Decimal (0 - 16777215) to a colour.
     *
     * @throws IllegalArgumentException when out of range
     */
    public static Color decToColor(long decValue) {
        if (decValue < 0 || decValue > 16777215L) {
            throw new IllegalArgumentException("Decimal colour out of range: " + decValue);
        }
        return new Color((int) decValue);
    }

    public static Color grayscaleColor(int value) {
        return new Color(value, value, value);
    }

    /** {@code FixHex}: one digit is doubled, two digits kept, anything else gives {@code null}. */
    public static String fixHex(String value) {
        if (value.length() == 1) {
            return value + value;
        }
        return value.length() == 2 ? value : null;
    }

    /** Closest web-safe colour as {@code RRGGBB} (no {@code #}). */
    public static String websafeColorHex(Color c) {
        return VB.hex2(websafe(c.getRed())) + VB.hex2(websafe(c.getGreen())) + VB.hex2(websafe(c.getBlue()));
    }

    public static Color websafeColor(Color c) {
        return new Color(websafe(c.getRed()), websafe(c.getGreen()), websafe(c.getBlue()));
    }

    public static boolean isWebsafeColor(Color c) {
        return toHtml(c).substring(1).equals(websafeColorHex(c));
    }

    private static int websafe(int channel) {
        return WEBSAFE_STEP * VB.cint(VB.round(channel / (double) WEBSAFE_STEP));
    }
}
