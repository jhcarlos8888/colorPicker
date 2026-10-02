package colorpicker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** Tests for {@link CursorFactory} (port of {@code ColorCursor}). */
class CursorFactoryTest {

    @Test
    void colorCursorShowsTheSwatchOverATransparentBackground() {
        BufferedImage img = CursorFactory.colorCursorImage(Color.RED);
        assertEquals(32, img.getWidth());
        assertEquals(32, img.getHeight());
        // 15x15 swatch in the top-right corner, outlined with the negative colour
        assertEquals(0xFFFF0000, img.getRGB(20, 5));
        assertEquals(0xFF00FFFF, img.getRGB(16, 5));
        // copy.cur: only the arrow and the '+' box are opaque
        assertEquals(0, img.getRGB(31, 31) >>> 24);
        assertEquals(0, img.getRGB(0, 31) >>> 24);
        assertEquals(0xFF000000, img.getRGB(1, 0));
        assertEquals(0xFFFFFFFF, img.getRGB(2, 2));
    }
}
