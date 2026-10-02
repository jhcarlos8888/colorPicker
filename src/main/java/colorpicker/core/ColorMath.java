package colorpicker.core;

/**
 * RGB &lt;-&gt; HSV conversions, ported line by line from
 * {@code ColorManager.RGBtoHSV} / {@code ColorManager.HSVtoRGB} including the
 * VB.NET banker's rounding.
 */
public final class ColorMath {

    private ColorMath() {
    }

    public static HSVColor rgbToHsv(int r, int g, int b, int a) {
        double h = 0;
        double s;
        double v;
        // R,G,B in [0, 1]
        double red = r / 255.0;
        double green = g / 255.0;
        double blue = b / 255.0;
        double max = Math.max(Math.max(red, green), blue);
        double min = Math.min(Math.min(red, green), blue);
        double chroma = max - min;
        // Hue (same case order as the VB "Select Case max")
        if (max == min) {
            h = 0;
        } else if (max == red) {
            h = (60 * (green - blue) / chroma + 360) % 360;
        } else if (max == green) {
            h = 60 * (blue - red) / chroma + 120;
        } else if (max == blue) {
            h = 60 * (red - green) / chroma + 240;
        }
        // Saturation
        s = max == 0 ? 0 : 1 - (min / max);
        // Value
        v = max;

        // V and S are percentages
        v = VB.round(v * 100);
        s = VB.round(s * 100);
        h = VB.round(h);
        return new HSVColor(VB.cint(h), VB.cint(s), VB.cint(v), a);
    }

    public static RGBColor hsvToRgb(int h, int s, int v, int a) {
        double r = 0;
        double g = 0;
        double b = 0;
        double hue = h;
        if (hue >= 360) {
            hue = hue - 360;
        }
        double sat = s / 100.0;
        double val = v / 100.0;
        double tempT = Math.floor((hue / 60) % 6);
        double f = hue / 60 - tempT;
        double l = val * (1 - sat);
        double m = val * (1 - f * sat);
        double n = val * (1 - (1 - f) * sat);

        switch ((int) tempT) {
            case 0 -> { r = val; g = n; b = l; }
            case 1 -> { r = m; g = val; b = l; }
            case 2 -> { r = l; g = val; b = n; }
            case 3 -> { r = l; g = m; b = val; }
            case 4 -> { r = n; g = l; b = val; }
            case 5 -> { r = val; g = l; b = m; }
            default -> { }
        }

        r = VB.round(r * 255);
        g = VB.round(g * 255);
        b = VB.round(b * 255);

        // The original used Color.FromArgb(r, g, b), so alpha is always opaque.
        return new RGBColor(VB.cint(r), VB.cint(g), VB.cint(b), 255);
    }
}
