package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Parity tests for {@link ColorChanger} (VB {@code ColorChanger} plus
 * {@code ColorTranslator.ToHtml/FromHtml}).
 */
class ColorChangerTest {

    @ParameterizedTest
    @ValueSource(strings = {"#abc", "#ABC", "#abcdef", "#ABCDEF", "#000000", "#fFfFfF", "#123", "#09aF3c"})
    void validHexCodes(String code) {
        assertTrue(ColorChanger.isValidHexColorCode(code));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"abc", "abcdef", "#abcd", "#ab", "#a", "#", "#abcdefa", "#abcde", "#ggg", "#12345g",
            "##abc", " #abc", "#abc ", "0xABCDEF", "#ab cd"})
    void invalidHexCodes(String code) {
        assertFalse(ColorChanger.isValidHexColorCode(code));
    }

    @ParameterizedTest(name = "toHtml({0},{1},{2}) = {3}")
    @CsvSource({
            "0,0,0, #000000",
            "255,255,255, #FFFFFF",
            "255,0,0, #FF0000",
            "1,2,3, #010203",
            "171,205,239, #ABCDEF",
            "12,34,56, #0C2238",
    })
    void toHtml(int r, int g, int b, String expected) {
        assertEquals(expected, ColorChanger.toHtml(r, g, b));
        assertEquals(expected, ColorChanger.toHtml(new Color(r, g, b)));
    }

    @Test
    void toHtmlIgnoresAlpha() {
        assertEquals("#102030", ColorChanger.toHtml(new Color(16, 32, 48, 0)));
    }

    @ParameterizedTest(name = "fromHtml({0}) = ({1},{2},{3})")
    @CsvSource({
            "#ABCDEF, 171,205,239",
            "#abcdef, 171,205,239",
            "abcdef, 171,205,239",
            "#abc, 170,187,204",
            "abc, 170,187,204",
            "#000, 0,0,0",
            "fff, 255,255,255",
            "#0C2238, 12,34,56",
            "'  #0c2238  ', 12,34,56",
    })
    void fromHtml(String text, int r, int g, int b) {
        Color c = ColorChanger.fromHtml(text);
        assertEquals(new Color(r, g, b), c);
        assertEquals(255, c.getAlpha());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"#", "#abcd", "abcd", "#abcde", "#abcdefa", "xyz", "#ggg", "##abc", "red", "0x123456", "12 34 56"})
    void fromHtmlInvalidGivesNull(String text) {
        assertNull(ColorChanger.fromHtml(text));
    }

    @Test
    void expandShortHex() {
        assertEquals("aabbcc", ColorChanger.expandShortHex("abc"));
        assertEquals("AB12CD", ColorChanger.expandShortHex("AB12CD"));
    }

    @ParameterizedTest(name = "HexToDec({0}) = {1}")
    @CsvSource({
            "#000000, 0",
            "#FFFFFF, 16777215",
            "#FF0000, 16711680",
            "#00FF00, 65280",
            "#0000FF, 255",
            "#0C2238, 795192",
            "FF, 255",
            "A, 10",
            "10, 16",
            "#abcdef, 11259375",
    })
    void hexToDec(String hex, int expected) {
        assertEquals(expected, ColorChanger.hexToDec(hex));
    }

    @Test
    void hexToDecFromChannels() {
        assertEquals(0, ColorChanger.hexToDec(0, 0, 0));
        assertEquals(16777215, ColorChanger.hexToDec(255, 255, 255));
        assertEquals(795192, ColorChanger.hexToDec(12, 34, 56));
        assertEquals(65536 + 2 * 256 + 3, ColorChanger.hexToDec(1, 2, 3));
    }

    @ParameterizedTest(name = "DecToColor({0}) = ({1},{2},{3}) {4}")
    @CsvSource({
            "0, 0,0,0, #000000",
            "16777215, 255,255,255, #FFFFFF",
            "795192, 12,34,56, #0C2238",
            "65536, 1,0,0, #010000",
            "256, 0,1,0, #000100",
            "255, 0,0,255, #0000FF",
    })
    void decToColor(int dec, int r, int g, int b, String html) {
        Color c = ColorChanger.decToColor(dec);
        assertEquals(new Color(r, g, b), c);
        assertEquals(255, c.getAlpha());
        assertEquals(html, ColorChanger.decToHex(dec));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 16777216, Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE})
    void decToColorOutOfRangeThrows(long dec) {
        assertThrows(IllegalArgumentException.class, () -> ColorChanger.decToColor(dec));
    }

    @Test
    void decToHexOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> ColorChanger.decToHex(-1));
        assertThrows(IllegalArgumentException.class, () -> ColorChanger.decToHex(16777216));
    }

    @Test
    void grayscaleColor() {
        assertEquals(new Color(7, 7, 7), ColorChanger.grayscaleColor(7));
    }

    @Test
    void fixHex() {
        assertEquals("aa", ColorChanger.fixHex("a"));
        assertEquals("ab", ColorChanger.fixHex("ab"));
        assertNull(ColorChanger.fixHex("abc"));
        assertNull(ColorChanger.fixHex(""));
    }

    /** {@code 51 * Math.Round(c / 51)}: x/51 is never exactly .5, so the boundaries are 25|26, 76|77... */
    @ParameterizedTest(name = "websafe channel {0} -> {1}")
    @CsvSource({
            "0, 0",
            "25, 0",
            "26, 51",
            "51, 51",
            "76, 51",
            "77, 102",
            "102, 102",
            "127, 102",
            "128, 153",
            "153, 153",
            "178, 153",
            "179, 204",
            "204, 204",
            "229, 204",
            "230, 255",
            "255, 255",
    })
    void websafeChannel(int channel, int expected) {
        Color c = new Color(channel, channel, channel);
        assertEquals(new Color(expected, expected, expected), ColorChanger.websafeColor(c));
        String hex = VB.hex2(expected);
        assertEquals(hex + hex + hex, ColorChanger.websafeColorHex(c));
        assertEquals(channel == expected, ColorChanger.isWebsafeColor(c));
    }

    @Test
    void websafeMixedChannels() {
        Color c = new Color(26, 77, 128);
        assertEquals(new Color(51, 102, 153), ColorChanger.websafeColor(c));
        assertEquals("336699", ColorChanger.websafeColorHex(c));
        assertEquals("CCFF00", ColorChanger.websafeColorHex(new Color(204, 240, 12)));
        assertEquals(255, ColorChanger.websafeColor(new Color(1, 2, 3, 10)).getAlpha());
    }

    @Test
    void isWebsafeColor() {
        assertTrue(ColorChanger.isWebsafeColor(new Color(0, 51, 102)));
        assertTrue(ColorChanger.isWebsafeColor(new Color(153, 204, 255)));
        assertTrue(ColorChanger.isWebsafeColor(Color.WHITE));
        assertTrue(ColorChanger.isWebsafeColor(Color.BLACK));
        assertFalse(ColorChanger.isWebsafeColor(new Color(0, 51, 103)));
        assertFalse(ColorChanger.isWebsafeColor(new Color(1, 0, 0)));
        assertFalse(ColorChanger.isWebsafeColor(new Color(12, 34, 56)));
    }

    @Test
    void websafeOfEveryValueIsWebsafe() {
        for (int v = 0; v < 256; v++) {
            Color w = ColorChanger.websafeColor(new Color(v, 255 - v, v / 2));
            assertTrue(ColorChanger.isWebsafeColor(w), "not websafe: " + w);
            assertTrue(Math.abs(w.getRed() - v) <= 25);
        }
    }
}
