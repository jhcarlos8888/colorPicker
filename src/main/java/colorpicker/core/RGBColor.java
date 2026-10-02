package colorpicker.core;

import java.awt.Color;

/**
 * An RGBA colour with 0-255 channels (port of {@code ColorManager.RGBColor}).
 */
public record RGBColor(int r, int g, int b, int a) {

    public RGBColor {
        r = clampByte(r);
        g = clampByte(g);
        b = clampByte(b);
        a = clampByte(a);
    }

    public RGBColor(int r, int g, int b) {
        this(r, g, b, 255);
    }

    public static RGBColor of(Color c) {
        return new RGBColor(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    public Color toColor() {
        return new Color(r, g, b, a);
    }

    public HSVColor toHSV() {
        return ColorMath.rgbToHsv(r, g, b, a);
    }

    /** {@code #RRGGBB} in upper case, like {@code ColorTranslator.ToHtml}. */
    public String toHtml() {
        return ColorChanger.toHtml(r, g, b);
    }

    @Override
    public String toString() {
        return "RGBA(" + r + ", " + g + ", " + b + ", " + a + ")";
    }

    static int clampByte(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
