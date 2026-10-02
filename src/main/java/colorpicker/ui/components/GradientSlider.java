package colorpicker.ui.components;

import colorpicker.core.HSVColor;
import colorpicker.core.VB;
import com.formdev.flatlaf.util.UIScale;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Horizontal colour slider: a gradient strip with tick marks and a
 * TrackBar-like thumb underneath. Replaces the {@code TrackBar} + gradient
 * {@code PictureBox} pairs of {@code Form1} ({@code red_bar}/{@code red_pic},
 * {@code hue_bar}/{@code hue_pic}, ...) and their {@code Scroll},
 * {@code pic_MouseDown} and {@code pic_MouseMove} handlers.
 *
 * <ul>
 * <li>Pressing the left button on the thumb grabs it without changing the
 * value, and dragging moves it relative to the grab point, like the TrackBar
 * thumb. Pressing anywhere else (the gradient strip, like {@code pic_MouseDown},
 * or the groove) sets the value to {@code round(x / gradientWidth * range)}
 * (banker's rounding, like VB), clamped, and dragging follows the pointer.
 * Any click focuses the slider.</li>
 * <li>Keyboard: arrows (&plusmn;1), Page Up/Down (&plusmn;5, the TrackBar's
 * {@code LargeChange}), Home/End; mouse wheel (3 per notch).</li>
 * <li>Only user interaction notifies the listeners added with
 * {@link #addUserChangeListener}; {@link #setValue} never does, like
 * {@code TrackBar.Scroll}.</li>
 * </ul>
 */
public class GradientSlider extends JComponent {

    private static final long serialVersionUID = 1L;

    /** Tick marks every 50 units ({@code TickFrequency}). */
    public static final int TICK_FREQUENCY = 50;
    /** {@code TrackBar.SmallChange}. */
    public static final int SMALL_CHANGE = 1;
    /** {@code TrackBar.LargeChange}. */
    public static final int LARGE_CHANGE = 5;
    /** Units per wheel notch ({@code SystemInformation.MouseWheelScrollLines} x SmallChange). */
    private static final int WHEEL_STEP = 3;

    // Unscaled geometry (pixels at 100 %); see px(int).
    private static final int GRADIENT_Y = 2;
    private static final int GRADIENT_HEIGHT = 10;
    private static final int TICK_LENGTH = 3;
    private static final int THUMB_HALF_WIDTH = 5;
    private static final int THUMB_HEIGHT = 12;
    private static final int FOCUS_WIDTH = 2;
    /** Horizontal room so the thumb and its focus ring stay inside at both ends. */
    private static final int PAD = THUMB_HALF_WIDTH + FOCUS_WIDTH + 1;
    private static final int PREF_WIDTH = 184;
    private static final int PREF_HEIGHT = 32;

    private final int minimum;
    private final int maximum;
    private int value;
    private Color gradientFrom = Color.BLACK;
    private Color gradientTo = Color.WHITE;
    private transient BufferedImage gradientImage;
    private boolean dragging;
    /** Pointer x minus the thumb centre when the thumb was grabbed; 0 after a jump. */
    private int grabOffset;
    private int pressX;
    private int pressValue;
    private boolean rollover;
    private double wheelRemainder;

    /**
     * @param minimum lowest value ({@code TrackBar.Minimum})
     * @param maximum highest value ({@code TrackBar.Maximum})
     */
    public GradientSlider(int minimum, int maximum) {
        if (maximum <= minimum) {
            throw new IllegalArgumentException("maximum must be greater than minimum");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.value = minimum;
        setFocusable(true);
        setOpaque(false);
        installMouseHandling();
        installKeyboardHandling();
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    /**
     * The hue spectrum (hue 0 to 360 at full saturation and value), computed
     * with the application's HSV conversion. Use with {@link #setGradientImage}.
     */
    public static BufferedImage hueSpectrum() {
        BufferedImage img = new BufferedImage(361, 1, BufferedImage.TYPE_INT_RGB);
        for (int h = 0; h <= 360; h++) {
            img.setRGB(h, 0, new HSVColor(h, 100, 100).toColor().getRGB());
        }
        return img;
    }

    public int getMinimum() {
        return minimum;
    }

    public int getMaximum() {
        return maximum;
    }

    public int getValue() {
        return value;
    }

    /** Sets the value from code (clamped). Never notifies the user-change listeners. */
    public void setValue(int newValue) {
        int v = clamp(newValue);
        if (v != value) {
            value = v;
            repaint();
        }
    }

    /** Paints a horizontal two-colour gradient ({@code MakeGradient}). */
    public void setGradient(Color from, Color to) {
        Objects.requireNonNull(from);
        Objects.requireNonNull(to);
        if (gradientImage == null && from.equals(gradientFrom) && to.equals(gradientTo)) {
            return;
        }
        gradientImage = null;
        gradientFrom = from;
        gradientTo = to;
        repaint();
    }

    /** Paints an image stretched over the gradient strip (e.g. {@link #hueSpectrum()}). */
    public void setGradientImage(BufferedImage image) {
        gradientImage = Objects.requireNonNull(image);
        repaint();
    }

    /** Listeners notified only when the user changes the value (mouse, keyboard, wheel). */
    public void addUserChangeListener(ChangeListener l) {
        listenerList.add(ChangeListener.class, l);
    }

    public void removeUserChangeListener(ChangeListener l) {
        listenerList.remove(ChangeListener.class, l);
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) {
            return super.getPreferredSize();
        }
        return new Dimension(px(PREF_WIDTH), px(PREF_HEIGHT));
    }

    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) {
            return super.getMinimumSize();
        }
        return new Dimension(px(2 * PAD + 60), px(PREF_HEIGHT));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        dragging = false;
        grabOffset = 0;
        repaint();
    }

    // ------------------------------------------------------------------ geometry

    private int range() {
        return maximum - minimum;
    }

    private int clamp(int v) {
        return v < minimum ? minimum : Math.min(v, maximum);
    }

    /** Scales a length with the user interface scale (large desktop fonts). */
    private static int px(int v) {
        return UIScale.scale(v);
    }

    private int stripX() {
        return px(PAD);
    }

    private int stripWidth() {
        return Math.max(1, getWidth() - 2 * px(PAD));
    }

    private int top() {
        return Math.max(0, (getHeight() - px(PREF_HEIGHT)) / 2);
    }

    private int gradientY() {
        return top() + px(GRADIENT_Y);
    }

    /** Top of the tick marks, between the gradient strip and the thumb. */
    private int tickY() {
        return gradientY() + px(GRADIENT_HEIGHT) + px(2);
    }

    /** Y of the thumb's tip; the thumb and the groove lie below it. Package-private for the tests. */
    int thumbTipY() {
        return tickY() + px(TICK_LENGTH) + px(1);
    }

    private boolean onThumb(int x, int y) {
        return Math.abs(x - valueToX(value)) <= px(THUMB_HALF_WIDTH) + px(FOCUS_WIDTH) && y >= thumbTipY();
    }

    /** X of a value on the gradient strip (the thumb's centre). Package-private for the tests. */
    int valueToX(int v) {
        return stripX() + (int) Math.round((v - minimum) * (double) stripWidth() / range());
    }

    /** {@code Math.Round(e.X / pic.Image.Width * max)} of the original, clamped. */
    private int xToValue(int x) {
        return clamp(minimum + VB.cint((x - stripX()) / (double) stripWidth() * range()));
    }

    // ------------------------------------------------------------------ input

    private void userSet(int newValue, boolean alwaysNotify) {
        int v = clamp(newValue);
        boolean changed = v != value;
        value = v;
        if (changed) {
            repaint();
        }
        if (changed || alwaysNotify) {
            fireUserChange();
        }
    }

    private void fireUserChange() {
        ChangeEvent event = new ChangeEvent(this);
        for (ChangeListener l : listenerList.getListeners(ChangeListener.class)) {
            l.stateChanged(event);
        }
    }

    private void installMouseHandling() {
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!isEnabled()) {
                    return;
                }
                requestFocusInWindow();
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragging = true;
                    if (onThumb(e.getX(), e.getY())) {
                        // the TrackBar thumb is grabbed without changing the value
                        grabOffset = e.getX() - valueToX(value);
                    } else {
                        grabOffset = 0;
                        userSet(xToValue(e.getX()), true);
                    }
                    pressX = e.getX();
                    pressValue = value;
                    repaint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragging && isEnabled()) {
                    // x -> value -> x does not round-trip, so no horizontal move keeps the value
                    userSet(e.getX() == pressX ? pressValue : xToValue(e.getX() - grabOffset), false);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (dragging && SwingUtilities.isLeftMouseButton(e)) {
                    dragging = false;
                    grabOffset = 0;
                    repaint();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                rollover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                rollover = false;
                repaint();
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (!isEnabled()) {
                    return;
                }
                // Wheel away from the user (negative rotation) increases, like the TrackBar.
                wheelRemainder -= e.getPreciseWheelRotation() * WHEEL_STEP;
                int steps = (int) wheelRemainder;
                if (steps != 0) {
                    wheelRemainder -= steps;
                    userSet(value + steps, false);
                }
                e.consume();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    private void installKeyboardHandling() {
        InputMap im = getInputMap(WHEN_FOCUSED);
        ActionMap am = getActionMap();
        bind(im, am, "decrement", new StepAction(this, -SMALL_CHANGE, false),
                "LEFT", "KP_LEFT", "DOWN", "KP_DOWN");
        bind(im, am, "increment", new StepAction(this, SMALL_CHANGE, false),
                "RIGHT", "KP_RIGHT", "UP", "KP_UP");
        bind(im, am, "largeIncrement", new StepAction(this, LARGE_CHANGE, false), "PAGE_UP");
        bind(im, am, "largeDecrement", new StepAction(this, -LARGE_CHANGE, false), "PAGE_DOWN");
        bind(im, am, "minimum", new StepAction(this, Integer.MIN_VALUE, true), "HOME");
        bind(im, am, "maximum", new StepAction(this, Integer.MAX_VALUE, true), "END");
    }

    private static void bind(InputMap im, ActionMap am, String name, AbstractAction action, String... keys) {
        for (String k : keys) {
            im.put(KeyStroke.getKeyStroke(k), name);
        }
        am.put(name, action);
    }

    /** Keyboard step: a relative change, or an absolute jump to minimum/maximum. */
    private static final class StepAction extends AbstractAction {
        private static final long serialVersionUID = 1L;
        private final GradientSlider slider;
        private final int amount;
        private final boolean absolute;

        StepAction(GradientSlider slider, int amount, boolean absolute) {
            this.slider = slider;
            this.amount = amount;
            this.absolute = absolute;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!slider.isEnabled()) {
                return;
            }
            if (absolute) {
                slider.userSet(amount < 0 ? slider.minimum : slider.maximum, false);
            } else {
                slider.userSet(slider.value + amount, false);
            }
        }
    }

    // ------------------------------------------------------------------ painting

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            paintSlider(g2);
        } finally {
            g2.dispose();
        }
    }

    private void paintSlider(Graphics2D g2) {
        int x0 = stripX();
        int w = stripWidth();
        int gy = gradientY();
        int gh = px(GRADIENT_HEIGHT);
        boolean enabled = isEnabled();
        boolean focused = isFocusOwner();

        // gradient strip
        Composite oldComposite = g2.getComposite();
        if (!enabled) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
        }
        if (gradientImage != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(gradientImage, x0, gy, w, gh, null);
        } else {
            g2.setPaint(new GradientPaint(x0, 0, gradientFrom, x0 + w - 1f, 0, gradientTo));
            g2.fillRect(x0, gy, w, gh);
        }
        g2.setComposite(oldComposite);
        g2.setColor(focused ? focusBorderColor() : ui("Component.borderColor", new Color(194, 194, 194)));
        g2.drawRect(x0 - 1, gy - 1, w + 1, gh + 1);

        // tick marks (TickStyle.TopLeft: between the strip and the thumb), first and last always drawn
        int ty = tickY();
        int tl = px(TICK_LENGTH);
        g2.setColor(enabled ? ui("Slider.tickColor", new Color(136, 136, 136))
                : ui("Slider.disabledTrackColor", new Color(200, 200, 200)));
        for (int t = minimum; t < maximum; t += TICK_FREQUENCY) {
            int x = valueToX(t);
            g2.drawLine(x, ty, x, ty + tl - 1);
        }
        int xm = valueToX(maximum);
        g2.drawLine(xm, ty, xm, ty + tl - 1);

        // track groove
        int tipY = thumbTipY();
        int groove = px(4);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(enabled ? ui("Slider.trackColor", new Color(196, 196, 196))
                : ui("Slider.disabledTrackColor", new Color(210, 210, 210)));
        g2.fillRoundRect(x0, tipY + px(6), w, groove, groove, groove);

        // thumb: a pentagon pointing up at the gradient
        int cx = valueToX(value);
        float half = px(THUMB_HALF_WIDTH) + 0.5f;
        Path2D.Float thumb = new Path2D.Float();
        thumb.moveTo(cx, tipY);
        thumb.lineTo(cx + half, tipY + half);
        thumb.lineTo(cx + half, tipY + px(THUMB_HEIGHT));
        thumb.lineTo(cx - half, tipY + px(THUMB_HEIGHT));
        thumb.lineTo(cx - half, tipY + half);
        thumb.closePath();
        if (focused) {
            g2.setColor(ui("Slider.focusedColor", focusBorderColor()));
            g2.setStroke(new BasicStroke(px(FOCUS_WIDTH) * 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(thumb);
        }
        g2.setColor(thumbColor(enabled));
        g2.fill(thumb);
    }

    private Color thumbColor(boolean enabled) {
        Color normal = ui("Slider.thumbColor", new Color(38, 117, 191));
        if (!enabled) {
            return ui("Slider.disabledThumbColor", new Color(192, 192, 192));
        }
        if (dragging) {
            return ui("Slider.pressedThumbColor", normal.darker());
        }
        if (rollover) {
            return ui("Slider.hoverThumbColor", normal);
        }
        return normal;
    }

    private static Color focusBorderColor() {
        Color c = UIManager.getColor("Component.focusedBorderColor");
        if (c == null) {
            c = UIManager.getColor("Component.focusColor");
        }
        return c != null ? c : new Color(137, 176, 212);
    }

    private static Color ui(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? c : fallback;
    }
}
