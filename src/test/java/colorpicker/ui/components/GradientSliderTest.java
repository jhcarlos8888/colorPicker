package colorpicker.ui.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.formdev.flatlaf.util.UIScale;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Mouse handling of {@link GradientSlider}: like the TrackBar thumb of the
 * original, grabbing the thumb does not change the value; a press on the
 * gradient strip jumps to the pointer ({@code pic_MouseDown}).
 */
class GradientSliderTest {

    @AfterEach
    void resetZoom() {
        UIScale.setZoomFactor(1f);
    }

    private static GradientSlider slider(int max, AtomicInteger notified) {
        GradientSlider s = new GradientSlider(0, max);
        s.setSize(s.getPreferredSize().width + UIScale.scale(31), s.getPreferredSize().height);
        s.addUserChangeListener(e -> notified.incrementAndGet());
        return s;
    }

    private static void mouse(GradientSlider s, int id, int x, int y) {
        int mods = id == MouseEvent.MOUSE_RELEASED ? 0 : MouseEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        s.dispatchEvent(new MouseEvent(s, id, 0L, mods, x, y, 1, false, button));
    }

    @ParameterizedTest
    @ValueSource(floats = {1f, 1.25f, 2f})
    void pressOnThumbKeepsValue(float zoom) {
        UIScale.setZoomFactor(zoom);
        for (int max : new int[] {100, 255, 360}) {
            AtomicInteger notified = new AtomicInteger();
            GradientSlider s = slider(max, notified);
            int y = s.thumbTipY() + UIScale.scale(6);
            for (int v = 0; v <= max; v += 7) {
                int half = UIScale.scale(5);
                for (int dx = -half; dx <= half; dx++) {
                    s.setValue(v);
                    notified.set(0);
                    int x = s.valueToX(v) + dx;
                    mouse(s, MouseEvent.MOUSE_PRESSED, x, y);
                    assertEquals(v, s.getValue(), "press, max " + max + ", dx " + dx);
                    mouse(s, MouseEvent.MOUSE_DRAGGED, x, y + UIScale.scale(2));
                    assertEquals(v, s.getValue(), "vertical drag, max " + max + ", dx " + dx);
                    mouse(s, MouseEvent.MOUSE_RELEASED, x, y);
                    assertEquals(0, notified.get());
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {1f, 1.25f})
    void draggingTheThumbIsRelativeToTheGrabPoint(float zoom) {
        UIScale.setZoomFactor(zoom);
        AtomicInteger notified = new AtomicInteger();
        GradientSlider s = slider(255, notified);
        int y = s.thumbTipY() + UIScale.scale(6);
        s.setValue(100);
        int cx = s.valueToX(100);
        int grab = UIScale.scale(3);
        mouse(s, MouseEvent.MOUSE_PRESSED, cx + grab, y);
        mouse(s, MouseEvent.MOUSE_DRAGGED, cx + grab + 20, y);
        int dragged = s.getValue();
        mouse(s, MouseEvent.MOUSE_RELEASED, cx + grab + 20, y);
        assertTrue(dragged > 100);
        assertTrue(notified.get() > 0);

        // the same spot pressed on the gradient strip jumps to the same value
        s.setValue(100);
        mouse(s, MouseEvent.MOUSE_PRESSED, cx + 20, UIScale.scale(5));
        mouse(s, MouseEvent.MOUSE_RELEASED, cx + 20, UIScale.scale(5));
        assertEquals(dragged, s.getValue());
    }

    @ParameterizedTest
    @ValueSource(floats = {1f, 1.25f})
    void pressOnStripJumpsAndAlwaysNotifies(float zoom) {
        UIScale.setZoomFactor(zoom);
        AtomicInteger notified = new AtomicInteger();
        GradientSlider s = slider(360, notified);
        s.setValue(0);
        int y = UIScale.scale(5);
        mouse(s, MouseEvent.MOUSE_PRESSED, s.getWidth() / 2, y);
        mouse(s, MouseEvent.MOUSE_RELEASED, s.getWidth() / 2, y);
        int jumped = s.getValue();
        assertTrue(jumped > 150 && jumped < 210, "jumped to " + jumped);
        assertEquals(1, notified.get());

        mouse(s, MouseEvent.MOUSE_PRESSED, s.getWidth() / 2, y);
        mouse(s, MouseEvent.MOUSE_RELEASED, s.getWidth() / 2, y);
        assertEquals(jumped, s.getValue());
        assertEquals(2, notified.get());
    }
}
