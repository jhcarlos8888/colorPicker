package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parity tests for {@link ColorManager} (VB {@code ColorManager} class). */
class ColorManagerTest {

    private ColorManager manager;

    @BeforeEach
    void setUp() {
        manager = new ColorManager();
    }

    @Test
    void initialStateIsOpaqueBlackInRgb() {
        assertEquals(new RGBColor(0, 0, 0, 255), manager.getRGB());
        assertEquals(new HSVColor(0, 0, 0, 255), manager.getHSV());
        assertEquals(ColorSpace.RGB, manager.getCurrentSpace());
        assertEquals(Color.BLACK, manager.getColor());
    }

    @Test
    void setRgbUpdatesHsvAndSpace() {
        manager.setHSV(new HSVColor(10, 10, 10));
        manager.setRGB(new RGBColor(12, 34, 56));
        assertEquals(new RGBColor(12, 34, 56, 255), manager.getRGB());
        assertEquals(new HSVColor(210, 79, 22, 255), manager.getHSV());
        assertEquals(ColorSpace.RGB, manager.getCurrentSpace());
        assertEquals(new Color(12, 34, 56), manager.getColor());
    }

    @Test
    void setRgbKeepsAlphaInBothSpaces() {
        manager.setRGB(new RGBColor(255, 0, 0, 128));
        assertEquals(new RGBColor(255, 0, 0, 128), manager.getRGB());
        assertEquals(new HSVColor(0, 100, 100, 128), manager.getHSV());
        // getColor() is always opaque
        assertEquals(new Color(255, 0, 0), manager.getColor());
    }

    @Test
    void setRgbFromAwtColor() {
        manager.setRGB(new Color(51, 153, 204));
        assertEquals(new RGBColor(51, 153, 204), manager.getRGB());
        assertEquals(new HSVColor(200, 75, 80), manager.getHSV());
    }

    @Test
    void setHsvKeepsHsvVerbatimAndUpdatesRgb() {
        // The hue survives even though saturation is 0 (a RGB -> HSV round trip would give 0).
        manager.setHSV(new HSVColor(200, 0, 50));
        assertEquals(new HSVColor(200, 0, 50, 255), manager.getHSV());
        assertEquals(new RGBColor(128, 128, 128, 255), manager.getRGB());
        assertEquals(ColorSpace.HSV, manager.getCurrentSpace());
    }

    @Test
    void setHsvKeepsHue360() {
        manager.setHSV(new HSVColor(360, 100, 100));
        assertEquals(360, manager.getHSV().h());
        assertEquals(new RGBColor(255, 0, 0), manager.getRGB());
    }

    @Test
    void setHsvKeepsLossyValueInsteadOfNormalising() {
        // (1,1,1) is black in RGB, but the HSV stays as set.
        manager.setHSV(new HSVColor(120, 100, 1));
        assertEquals(new HSVColor(120, 100, 1), manager.getHSV());
        assertEquals(new RGBColor(0, 3, 0), manager.getRGB());
    }

    @Test
    void setHsvGivesOpaqueRgb() {
        // HSVtoRGB used Color.FromArgb(r, g, b): the RGB side is opaque, the HSV keeps its alpha.
        manager.setHSV(new HSVColor(120, 100, 100, 40));
        assertEquals(new RGBColor(0, 255, 0, 255), manager.getRGB());
        assertEquals(40, manager.getHSV().a());
    }

    @Test
    void currentSpaceCanBeSet() {
        manager.setCurrentSpace(ColorSpace.HSV);
        assertEquals(ColorSpace.HSV, manager.getCurrentSpace());
        assertThrows(NullPointerException.class, () -> manager.setCurrentSpace(null));
    }

    @Test
    void nullValuesAreRejected() {
        assertThrows(NullPointerException.class, () -> manager.setRGB((RGBColor) null));
        assertThrows(NullPointerException.class, () -> manager.setHSV(null));
    }

    @Test
    void colorChangedFiresOnEverySetEvenWithSameValue() {
        int[] count = {0};
        manager.addColorChangedListener(() -> count[0]++);
        manager.setRGB(new RGBColor(1, 2, 3));
        manager.setRGB(new RGBColor(1, 2, 3));
        manager.setHSV(new HSVColor(0, 0, 0));
        manager.setHSV(new HSVColor(0, 0, 0));
        manager.setRGB(Color.RED);
        assertEquals(5, count[0]);
    }

    @Test
    void colorChangedListenerSeesNewState() {
        List<RGBColor> seen = new ArrayList<>();
        manager.addColorChangedListener(() -> seen.add(manager.getRGB()));
        manager.setHSV(new HSVColor(240, 100, 100));
        assertEquals(List.of(new RGBColor(0, 0, 255)), seen);
    }

    @Test
    void beforeColorChangeReceivesNewValueBeforeTheChange() {
        List<String> events = new ArrayList<>();
        manager.setRGB(new RGBColor(9, 9, 9));
        manager.addBeforeColorChangeListener(value ->
                events.add("before " + value + " while " + manager.getRGB()));
        manager.addColorChangedListener(() -> events.add("changed " + manager.getRGB()));

        RGBColor rgb = new RGBColor(1, 2, 3);
        manager.setRGB(rgb);
        HSVColor hsv = new HSVColor(0, 100, 100);
        manager.setHSV(hsv);

        assertEquals(List.of(
                "before RGBA(1, 2, 3, 255) while RGBA(9, 9, 9, 255)",
                "changed RGBA(1, 2, 3, 255)",
                "before HSVA(0º, 100%, 100%, 255) while RGBA(1, 2, 3, 255)",
                "changed RGBA(255, 0, 0, 255)"), events);
    }

    @Test
    void beforeColorChangeValueIsTheExactObjectPassed() {
        Object[] received = new Object[1];
        manager.addBeforeColorChangeListener(v -> received[0] = v);
        HSVColor hsv = new HSVColor(10, 20, 30);
        manager.setHSV(hsv);
        assertSame(hsv, received[0]);
    }

    @Test
    void removedListenersAreNotCalled() {
        int[] changed = {0};
        int[] before = {0};
        Runnable l = () -> changed[0]++;
        Consumer<Object> b = v -> before[0]++;
        manager.addColorChangedListener(l);
        manager.addBeforeColorChangeListener(b);
        manager.setRGB(new RGBColor(1, 1, 1));
        manager.removeColorChangedListener(l);
        manager.removeBeforeColorChangeListener(b);
        manager.setRGB(new RGBColor(2, 2, 2));
        assertEquals(1, changed[0]);
        assertEquals(1, before[0]);
    }

    @Test
    void listenerMayRemoveItselfWhileFiring() {
        int[] count = {0};
        Runnable[] self = new Runnable[1];
        self[0] = () -> {
            count[0]++;
            manager.removeColorChangedListener(self[0]);
        };
        manager.addColorChangedListener(self[0]);
        manager.setRGB(new RGBColor(1, 1, 1));
        manager.setRGB(new RGBColor(2, 2, 2));
        assertEquals(1, count[0]);
    }
}
