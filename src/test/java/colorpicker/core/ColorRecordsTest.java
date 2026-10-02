package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;

/** Tests for the {@link RGBColor} / {@link HSVColor} value types. */
class ColorRecordsTest {

    @Test
    void rgbDefaultsToOpaque() {
        assertEquals(new RGBColor(1, 2, 3, 255), new RGBColor(1, 2, 3));
    }

    @Test
    void rgbChannelsAreClamped() {
        assertEquals(new RGBColor(0, 255, 0, 255), new RGBColor(-5, 300, 0, 999));
    }

    @Test
    void hsvRangesAreClamped() {
        assertEquals(new HSVColor(360, 100, 0, 0), new HSVColor(400, 150, -1, -3));
        assertEquals(new HSVColor(0, 0, 0, 255), new HSVColor(-10, 0, 0));
    }

    @Test
    void toStringMatchesOriginalFormat() {
        // "RGBA(" & R & ", " & G & ", " & B & ", " & A & ")"
        assertEquals("RGBA(1, 2, 3, 255)", new RGBColor(1, 2, 3).toString());
        // "HSVA(" & H & "º, " & S & "%, " & V & "%, " & A & ")"
        assertEquals("HSVA(210º, 79%, 22%, 128)", new HSVColor(210, 79, 22, 128).toString());
    }

    @Test
    void conversions() {
        RGBColor rgb = new RGBColor(12, 34, 56, 200);
        assertEquals(new HSVColor(210, 79, 22, 200), rgb.toHSV());
        assertEquals(new Color(12, 34, 56, 200), rgb.toColor());
        assertEquals("#0C2238", rgb.toHtml());
        assertEquals(rgb, RGBColor.of(new Color(12, 34, 56, 200)));

        HSVColor hsv = new HSVColor(210, 79, 22, 200);
        assertEquals(new RGBColor(12, 34, 56, 255), hsv.toRGB());
        assertEquals(new Color(12, 34, 56), hsv.toColor());
        assertEquals(new HSVColor(210, 79, 22, 77), HSVColor.of(new Color(12, 34, 56, 77)));
    }
}
