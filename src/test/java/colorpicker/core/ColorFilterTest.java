package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Parity tests for {@link ColorFilter} ({@code Form1.ApplyColorFilter}).
 * Expected values come from an independent re-implementation of the VB code.
 */
class ColorFilterTest {

    private static void assertFilter(ColorFilter f, int r, int g, int b, int er, int eg, int eb) {
        Color expected = new Color(er, eg, eb);
        assertEquals(expected, f.apply(r, g, b), f + " of " + r + "," + g + "," + b);
        assertEquals(expected, f.apply(new Color(r, g, b)));
        assertEquals(new RGBColor(er, eg, eb), f.apply(new RGBColor(r, g, b)));
    }

    /** Luma is the HSV value: V / 100 * 255 converted with banker's rounding. */
    @ParameterizedTest(name = "GRAYSCALE({0},{1},{2}) = {3}")
    @CsvSource({
            "0,0,0, 0",
            "255,255,255, 255",
            "126,126,126, 125",
            "125,125,125, 125",
            "200,100,50, 199",
            "12,34,56, 56",
            "100,149,237, 237",
            "3,2,1, 3",
            // V = 30 -> 76.5 -> 76 (half-up would give 77)
            "77,0,0, 76",
            "0,76,0, 76",
            // V = 70 -> 178.5 -> 178
            "0,0,179, 178",
            // V = 50 -> 127.5 -> 128, V = 10 -> 25.5 -> 26
            "128,0,0, 128",
            "26,26,0, 26",
    })
    void grayscale(int r, int g, int b, int luma) {
        assertFilter(ColorFilter.GRAYSCALE, r, g, b, luma, luma, luma);
    }

    @Test
    void negative() {
        assertFilter(ColorFilter.NEGATIVE, 0, 0, 0, 255, 255, 255);
        assertFilter(ColorFilter.NEGATIVE, 12, 34, 56, 243, 221, 199);
        assertFilter(ColorFilter.NEGATIVE, 255, 128, 0, 0, 127, 255);
    }

    @ParameterizedTest(name = "SEPIA({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            "0,0,0, 0,0,0",
            // clipping of R and G
            "255,255,255, 255,255,239",
            "250,250,250, 255,255,234",
            "250,240,10, 255,254,197",
            "126,126,126, 170,152,118",
            "200,100,50, 165,147,114",
            "100,200,50, 203,181,141",
            "50,100,200, 134,120,93",
            "12,34,56, 41,37,29",
            "3,2,1, 3,3,2",
            // exact x.5 before the implicit Integer conversion: banker's rounding
            "1,203,0, 156,140,109",
            "35,105,0, 94,84,66",
            "42,26,0, 36,32,25",
            "28,184,0, 152,136,106",
            "48,244,0, 206,184,143",
            "6,21,0, 19,16,13",
            "30,105,0, 93,82,64",
    })
    void sepia(int r, int g, int b, int er, int eg, int eb) {
        assertFilter(ColorFilter.SEPIA, r, g, b, er, eg, eb);
    }

    @Test
    void brighterClipsAt255() {
        assertFilter(ColorFilter.BRIGHTER, 0, 0, 0, 20, 20, 20);
        assertFilter(ColorFilter.BRIGHTER, 200, 100, 50, 220, 120, 70);
        assertFilter(ColorFilter.BRIGHTER, 250, 235, 236, 255, 255, 255);
        assertFilter(ColorFilter.BRIGHTER, 100, 149, 237, 120, 169, 255);
    }

    @Test
    void darkerClipsAt0() {
        assertFilter(ColorFilter.DARKER, 255, 255, 255, 235, 235, 235);
        assertFilter(ColorFilter.DARKER, 200, 100, 50, 180, 80, 30);
        assertFilter(ColorFilter.DARKER, 12, 34, 56, 0, 14, 36);
        assertFilter(ColorFilter.DARKER, 19, 20, 21, 0, 0, 1);
    }

    @ParameterizedTest(name = "INTENSIFY({0},{1},{2}) = ({3},{4},{5})")
    @CsvSource({
            // grayscale: >= 126 -> Brighter, < 126 -> Darker
            "126,126,126, 146,146,146",
            "125,125,125, 105,105,105",
            "250,250,250, 255,255,255",
            "10,10,10, 0,0,0",
            "0,0,0, 0,0,0",
            "255,255,255, 255,255,255",
            // unique max red: R+20, G += 3+Round(newR/255*5), B += 2+Round(newR/255*5)
            "200,100,50, 220,107,56",
            "200,50,50, 220,57,56",
            "3,2,1, 23,5,3",
            "250,240,10, 255,248,17",
            "77,0,0, 97,5,4",
            // unique max green: G+20, R += 2+..., B += 3+...
            "100,200,50, 106,220,57",
            "50,200,50, 56,220,57",
            "2,3,1, 4,23,4",
            // unique max blue: B+20, R += 2+..., G += 3+...
            "50,100,200, 56,107,220",
            "50,50,200, 56,57,220",
            "1,2,3, 3,5,23",
            "12,34,56, 15,38,76",
            "100,149,237, 107,157,255",
            "240,10,250, 247,18,255",
            // "except" branch, max shared by R and G: R+20, G+20, B += 3+Round(newR/255*5)
            "200,200,50, 220,220,57",
            "255,255,0, 255,255,8",
            "250,250,5, 255,255,13",
            // "except" branch, max shared by R and B
            "200,50,200, 220,57,220",
            "255,0,255, 255,8,255",
            "250,5,250, 255,13,255",
            // "except" branch, max shared by G and B
            "50,200,200, 57,220,220",
            "0,255,255, 8,255,255",
            "5,250,250, 13,255,255",
            "10,250,240, 17,255,248",
    })
    void intensify(int r, int g, int b, int er, int eg, int eb) {
        assertFilter(ColorFilter.INTENSIFY, r, g, b, er, eg, eb);
    }

    @Test
    void singleChannels() {
        assertFilter(ColorFilter.RED_ONLY, 12, 34, 56, 12, 0, 0);
        assertFilter(ColorFilter.GREEN_ONLY, 12, 34, 56, 0, 34, 0);
        assertFilter(ColorFilter.BLUE_ONLY, 12, 34, 56, 0, 0, 56);
    }

    @Test
    void rgbShuffle() {
        // Color.FromArgb(B, R, G)
        assertFilter(ColorFilter.RGB_SHUFFLE, 12, 34, 56, 56, 12, 34);
        assertFilter(ColorFilter.RGB_SHUFFLE, 255, 0, 128, 128, 255, 0);
    }

    @ParameterizedTest
    @EnumSource(ColorFilter.class)
    void resultsAreOpaque(ColorFilter f) {
        assertEquals(255, f.apply(new Color(10, 20, 30, 40)).getAlpha());
        assertEquals(255, f.apply(new RGBColor(10, 20, 30, 40)).a());
    }

    @Test
    void clippedColor() {
        assertEquals(new Color(0, 255, 128), ColorFilter.clippedColor(-5, 300, 128));
        assertEquals(new Color(0, 0, 255), ColorFilter.clippedColor(0, -1, 256));
    }

    @Test
    void filterOrderMatchesOriginalButtons() {
        assertEquals(10, ColorFilter.values().length);
        assertEquals(ColorFilter.GRAYSCALE, ColorFilter.values()[0]);
        assertEquals(ColorFilter.RGB_SHUFFLE, ColorFilter.values()[9]);
    }
}
