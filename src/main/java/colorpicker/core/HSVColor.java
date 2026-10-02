package colorpicker.core;

import java.awt.Color;

/**
 * An HSV colour (port of {@code ColorManager.HSVColor}): hue in degrees
 * (0-360), saturation and value in percent (0-100), alpha 0-255.
 */
public record HSVColor(int h, int s, int v, int a) {

    public HSVColor {
        h = h < 0 ? 0 : Math.min(h, 360);
        s = s < 0 ? 0 : Math.min(s, 100);
        v = v < 0 ? 0 : Math.min(v, 100);
        a = RGBColor.clampByte(a);
    }

    public HSVColor(int h, int s, int v) {
        this(h, s, v, 255);
    }

    public static HSVColor of(Color c) {
        return ColorMath.rgbToHsv(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    public RGBColor toRGB() {
        return ColorMath.hsvToRgb(h, s, v, a);
    }

    public Color toColor() {
        return toRGB().toColor();
    }

    @Override
    public String toString() {
        return "HSVA(" + h + "º, " + s + "%, " + v + "%, " + a + ")";
    }
}
