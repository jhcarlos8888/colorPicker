package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Parity tests for {@link ColorMath} against {@code ColorManager.RGBtoHSV} /
 * {@code ColorManager.HSVtoRGB} of the VB.NET original.
 *
 * <p>Every expected value was computed with an independent re-implementation
 * of the VB code (IEEE doubles, same operation order, {@code Math.Round} and
 * implicit Double to Integer conversions as round-half-to-even).
 */
class ColorMathTest {

    @ParameterizedTest(name = "rgbToHsv({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            // black, white, grays (V is rounded: 1 -> 0.39% -> 0, 254 -> 99.6% -> 100)
            "0,0,0, 0,0,0",
            "255,255,255, 0,0,100",
            "128,128,128, 0,0,50",
            "127,127,127, 0,0,50",
            "1,1,1, 0,0,0",
            "254,254,254, 0,0,100",
            // primaries
            "255,0,0, 0,100,100",
            "0,255,0, 120,100,100",
            "0,0,255, 240,100,100",
            // secondaries
            "255,255,0, 60,100,100",
            "0,255,255, 180,100,100",
            "255,0,255, 300,100,100",
            // hue wrap: 359.76 rounds to 360 (kept, not wrapped to 0)
            "255,0,1, 360,100,100",
            "255,1,0, 0,100,100",
            // arbitrary colours
            "12,34,56, 210,79,22",
            "200,100,50, 20,75,78",
            "51,153,204, 200,75,80",
            "250,128,114, 6,54,98",
            "100,149,237, 219,58,93",
            "173,255,47, 84,82,100",
            "75,0,130, 275,100,51",
            "210,105,30, 25,86,82",
            "123,45,67, 343,63,48",
            "10,200,10, 120,95,78",
            "255,128,0, 30,100,100",
            "64,224,208, 174,71,88",
            "0,0,1, 240,100,0",
            "3,0,0, 0,100,1",
            "77,0,0, 0,100,30",
            "76,0,0, 0,100,30",
    })
    void rgbToHsv(int r, int g, int b, int h, int s, int v) {
        assertEquals(new HSVColor(h, s, v, 255), ColorMath.rgbToHsv(r, g, b, 255));
    }

    /** max == red == green (red case first), max == red == blue, max == green == blue. */
    @ParameterizedTest(name = "tie rgbToHsv({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            "200,200,100, 60,50,78",
            "255,255,254, 60,0,100",
            "255,255,1, 60,100,100",
            "200,100,200, 300,50,78",
            "100,200,200, 180,50,78",
            "1,255,255, 180,100,100",
    })
    void rgbToHsvMaxTies(int r, int g, int b, int h, int s, int v) {
        assertEquals(new HSVColor(h, s, v, 255), ColorMath.rgbToHsv(r, g, b, 255));
    }

    /**
     * Inputs whose unrounded H or S is exactly x.5 in double arithmetic: VB's
     * banker's rounding gives the even neighbour (half-up would differ where
     * the integer part is even).
     */
    @ParameterizedTest(name = "banker rgbToHsv({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            // S = 62.5 -> 62, 12.5 -> 12, 37.5 -> 38, 87.5 -> 88
            "8,3,3, 0,62,3",
            "8,7,7, 0,12,3",
            "8,5,5, 0,38,3",
            "8,1,1, 0,88,3",
            // H = 22.5 -> 22 (red case), 322.5 -> 322 (red case, wrapped)
            "8,3,0, 22,100,3",
            "8,0,5, 322,100,3",
            // H = 132.5 -> 132, 157.5 -> 158 (green case)
            "0,24,5, 132,100,9",
            "0,24,15, 158,100,9",
            // H = 238.5 -> 238, 235.5 -> 236 (blue case)
            "0,3,120, 238,100,47",
            "0,3,40, 236,100,16",
    })
    void rgbToHsvBankersRounding(int r, int g, int b, int h, int s, int v) {
        assertEquals(new HSVColor(h, s, v, 255), ColorMath.rgbToHsv(r, g, b, 255));
    }

    @Test
    void rgbToHsvKeepsAlpha() {
        assertEquals(new HSVColor(0, 100, 100, 7), ColorMath.rgbToHsv(255, 0, 0, 7));
        assertEquals(new HSVColor(0, 0, 0, 0), ColorMath.rgbToHsv(0, 0, 0, 0));
    }

    @ParameterizedTest(name = "hsvToRgb({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            "0,0,0, 0,0,0",
            "0,0,100, 255,255,255",
            "0,100,100, 255,0,0",
            "60,100,100, 255,255,0",
            "120,100,100, 0,255,0",
            "180,100,100, 0,255,255",
            "240,100,100, 0,0,255",
            "300,100,100, 255,0,255",
            // 360 is treated as 0
            "360,100,100, 255,0,0",
            "359,100,100, 255,0,4",
            // saturation 0: the hue is irrelevant
            "200,0,50, 128,128,128",
            "30,50,50, 128,96,64",
            "200,75,80, 51,153,204",
            "210,79,22, 12,34,56",
            "90,40,60, 122,153,92",
            "330,20,90, 230,184,207",
            "45,100,50, 128,96,0",
            "150,33,67, 114,171,143",
            "270,66,33, 56,29,84",
            "0,50,100, 255,128,128",
            "359,1,1, 3,3,3",
    })
    void hsvToRgb(int h, int s, int v, int r, int g, int b) {
        assertEquals(new RGBColor(r, g, b, 255), ColorMath.hsvToRgb(h, s, v, 255));
    }

    /** Channels that are exactly x.5 before rounding (banker's rounding to even). */
    @ParameterizedTest(name = "banker hsvToRgb({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            // V% * 2.55: 25.5 -> 26, 76.5 -> 76, 127.5 -> 128, 178.5 -> 178, 229.5 -> 230
            "0,0,10, 26,26,26",
            "0,0,30, 76,76,76",
            "0,0,50, 128,128,128",
            "0,0,70, 178,178,178",
            "0,0,90, 230,230,230",
            // non-max channels at x.5
            "0,30,100, 255,178,178",
            "1,40,50, 128,77,76",
            "0,1,70, 178,177,177",
    })
    void hsvToRgbBankersRounding(int h, int s, int v, int r, int g, int b) {
        assertEquals(new RGBColor(r, g, b, 255), ColorMath.hsvToRgb(h, s, v, 255));
    }

    @Test
    void hsvToRgbIsAlwaysOpaque() {
        // The original returned Color.FromArgb(r, g, b), dropping the HSV alpha.
        assertEquals(255, ColorMath.hsvToRgb(120, 50, 50, 10).a());
    }

    /**
     * Hash of {@code rgbToHsv} over the whole 24-bit RGB space, compared to the
     * hash produced by the independent reference implementation.
     */
    @Test
    void rgbToHsvWholeSpaceMatchesReference() {
        long acc = 17;
        for (int r = 0; r < 256; r++) {
            for (int g = 0; g < 256; g++) {
                for (int b = 0; b < 256; b++) {
                    HSVColor c = ColorMath.rgbToHsv(r, g, b, 255);
                    acc = acc * 31 + c.h();
                    acc = acc * 31 + c.s();
                    acc = acc * 31 + c.v();
                }
            }
        }
        assertEquals(6905715318133986951L, acc);
    }

    /** Same for {@code hsvToRgb} over every H 0-360, S 0-100, V 0-100. */
    @Test
    void hsvToRgbWholeSpaceMatchesReference() {
        long acc = 17;
        for (int h = 0; h <= 360; h++) {
            for (int s = 0; s <= 100; s++) {
                for (int v = 0; v <= 100; v++) {
                    RGBColor c = ColorMath.hsvToRgb(h, s, v, 255);
                    acc = acc * 31 + c.r();
                    acc = acc * 31 + c.g();
                    acc = acc * 31 + c.b();
                }
            }
        }
        assertEquals(-6771020347689110334L, acc);
    }

    /**
     * The original conversion is lossy (whole degrees and whole percents).
     * Over every 5th value of each channel the reference gives: 140608
     * colours, max per-channel error 3, 22138 exact round trips. The worst
     * case is (0,155,200) -> (194,100,78) -> (0,152,199).
     */
    @Test
    void roundTripErrorOnSample() {
        int maxError = 0;
        int exact = 0;
        int n = 0;
        for (int r = 0; r < 256; r += 5) {
            for (int g = 0; g < 256; g += 5) {
                for (int b = 0; b < 256; b += 5) {
                    RGBColor back = ColorMath.rgbToHsv(r, g, b, 255).toRGB();
                    int e = Math.max(Math.abs(r - back.r()), Math.max(Math.abs(g - back.g()), Math.abs(b - back.b())));
                    maxError = Math.max(maxError, e);
                    if (e == 0) {
                        exact++;
                    }
                    n++;
                }
            }
        }
        assertEquals(140608, n);
        assertEquals(3, maxError);
        assertEquals(22138, exact);
        assertEquals(new RGBColor(0, 152, 199), ColorMath.rgbToHsv(0, 155, 200, 255).toRGB());
    }

    /** Whole space: max error 3 and 2043994 exact round trips out of 16777216. */
    @Test
    void roundTripErrorWholeSpace() {
        int maxError = 0;
        int exact = 0;
        for (int r = 0; r < 256; r++) {
            for (int g = 0; g < 256; g++) {
                for (int b = 0; b < 256; b++) {
                    RGBColor back = ColorMath.rgbToHsv(r, g, b, 255).toRGB();
                    int e = Math.max(Math.abs(r - back.r()), Math.max(Math.abs(g - back.g()), Math.abs(b - back.b())));
                    maxError = Math.max(maxError, e);
                    if (e == 0) {
                        exact++;
                    }
                }
            }
        }
        assertEquals(3, maxError);
        assertEquals(2043994, exact);
    }
}
