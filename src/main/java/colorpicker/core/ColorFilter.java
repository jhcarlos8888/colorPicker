package colorpicker.core;

import java.awt.Color;

/**
 * The colour filters of the "Filters" tab (port of {@code Form1.ColorFilters}
 * and {@code Form1.ApplyColorFilter}). All results are opaque.
 */
public enum ColorFilter {
    GRAYSCALE,
    NEGATIVE,
    SEPIA,
    BRIGHTER,
    DARKER,
    INTENSIFY,
    RED_ONLY,
    GREEN_ONLY,
    BLUE_ONLY,
    RGB_SHUFFLE;

    public Color apply(Color c) {
        return apply(c.getRed(), c.getGreen(), c.getBlue());
    }

    public RGBColor apply(RGBColor c) {
        return RGBColor.of(apply(c.r(), c.g(), c.b()));
    }

    public Color apply(int r, int g, int b) {
        switch (this) {
            case GRAYSCALE: {
                // Uses the HSV value (not luma).
                int luma = VB.cint(ColorMath.rgbToHsv(r, g, b, 255).v() / 100.0 * 255);
                return new Color(luma, luma, luma);
            }
            case NEGATIVE:
                return new Color(255 - r, 255 - g, 255 - b);
            case SEPIA: {
                double outR = (r * 0.393) + (g * 0.769) + (b * 0.189);
                double outG = (r * 0.349) + (g * 0.686) + (b * 0.168);
                double outB = (r * 0.272) + (g * 0.534) + (b * 0.131);
                return clippedColor(VB.cint(outR), VB.cint(outG), VB.cint(outB));
            }
            case BRIGHTER:
                return clippedColor(r + 20, g + 20, b + 20);
            case DARKER:
                return clippedColor(r - 20, g - 20, b - 20);
            case INTENSIFY:
                return intensify(r, g, b);
            case RED_ONLY:
                return new Color(r, 0, 0);
            case GREEN_ONLY:
                return new Color(0, g, 0);
            case BLUE_ONLY:
                return new Color(0, 0, b);
            case RGB_SHUFFLE:
                return new Color(b, r, g);
            default:
                throw new IllegalStateException(name());
        }
    }

    /** Channel clamping to 0-255 (port of {@code ClippedColor}). */
    public static Color clippedColor(int r, int g, int b) {
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }

    /** Line-by-line port of the "Intensify" branch, including its GoTo. */
    private static Color intensify(int r, int g, int b) {
        int outR = r;
        int outG = g;
        int outB = b;
        if (outR == outG && outR == outB) {
            // grayscale
            return outR >= 126 ? BRIGHTER.apply(r, g, b) : DARKER.apply(r, g, b);
        }
        int max = Math.max(Math.max(outR, outG), outB);
        boolean except;
        if (max == outR) {
            except = outR == outG || outR == outB;
            if (!except) {
                outR += 20;
                outG += 3 + VB.cint(VB.round(outR / 255.0 * 5));
                outB += 2 + VB.cint(VB.round(outR / 255.0 * 5));
            }
        } else if (max == outG) {
            except = outG == outR || outG == outB;
            if (!except) {
                outG += 20;
                outR += 2 + VB.cint(VB.round(outG / 255.0 * 5));
                outB += 3 + VB.cint(VB.round(outG / 255.0 * 5));
            }
        } else {
            except = outB == outR || outB == outG;
            if (!except) {
                outB += 20;
                outR += 2 + VB.cint(VB.round(outB / 255.0 * 5));
                outG += 3 + VB.cint(VB.round(outB / 255.0 * 5));
            }
        }
        if (!except) {
            return clippedColor(outR, outG, outB);
        }
        // "except:" label of the original
        if (outR == outG) {
            outR += 20;
            outG += 20;
            outB += 3 + VB.cint(VB.round(outR / 255.0 * 5));
        } else if (outR == outB) {
            outR += 20;
            outB += 20;
            outG += 3 + VB.cint(VB.round(outB / 255.0 * 5));
        } else {
            // outB == outG
            outB += 20;
            outG += 20;
            outR += 3 + VB.cint(VB.round(outG / 255.0 * 5));
        }
        return clippedColor(outR, outG, outB);
    }
}
