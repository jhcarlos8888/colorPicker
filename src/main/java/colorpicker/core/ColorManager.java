package colorpicker.core;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Holds the current colour in both RGB and HSV (port of {@code ColorManager}).
 *
 * <p>Setting the RGB value recomputes HSV and vice versa. Setting HSV keeps the
 * HSV value exactly as given (so the hue survives when saturation is 0), just
 * like the original. {@code ColorChanged} is raised on every set, even when
 * the value did not change.
 *
 * <p>Not thread safe: use it from the Swing event dispatch thread only.
 */
public final class ColorManager {

    private ColorSpace colorSpace = ColorSpace.RGB;
    private RGBColor rgb = new RGBColor(0, 0, 0, 255);
    private HSVColor hsv = rgb.toHSV();

    private final List<Runnable> colorChangedListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<Object>> beforeColorChangeListeners = new CopyOnWriteArrayList<>();

    public ColorSpace getCurrentSpace() {
        return colorSpace;
    }

    public void setCurrentSpace(ColorSpace space) {
        colorSpace = Objects.requireNonNull(space);
    }

    public RGBColor getRGB() {
        return rgb;
    }

    public void setRGB(RGBColor value) {
        Objects.requireNonNull(value);
        fireBeforeColorChange(value);
        rgb = value;
        hsv = value.toHSV();
        colorSpace = ColorSpace.RGB;
        fireColorChanged();
    }

    /** Convenience for {@code setRGB(RGBColor.of(color))}. */
    public void setRGB(Color color) {
        setRGB(RGBColor.of(color));
    }

    public HSVColor getHSV() {
        return hsv;
    }

    public void setHSV(HSVColor value) {
        Objects.requireNonNull(value);
        fireBeforeColorChange(value);
        hsv = value;
        rgb = value.toRGB();
        colorSpace = ColorSpace.HSV;
        fireColorChanged();
    }

    /** The current colour as an opaque AWT colour. */
    public Color getColor() {
        return new Color(rgb.r(), rgb.g(), rgb.b());
    }

    public void addColorChangedListener(Runnable listener) {
        colorChangedListeners.add(Objects.requireNonNull(listener));
    }

    public void removeColorChangedListener(Runnable listener) {
        colorChangedListeners.remove(listener);
    }

    /** The listener receives the new value (an {@link RGBColor} or {@link HSVColor}). */
    public void addBeforeColorChangeListener(Consumer<Object> listener) {
        beforeColorChangeListeners.add(Objects.requireNonNull(listener));
    }

    public void removeBeforeColorChangeListener(Consumer<Object> listener) {
        beforeColorChangeListeners.remove(listener);
    }

    private void fireBeforeColorChange(Object newValue) {
        for (Consumer<Object> l : beforeColorChangeListeners) {
            l.accept(newValue);
        }
    }

    private void fireColorChanged() {
        for (Runnable l : colorChangedListeners) {
            l.run();
        }
    }
}
