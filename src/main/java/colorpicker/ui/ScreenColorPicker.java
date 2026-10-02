package colorpicker.ui;

import colorpicker.App;
import colorpicker.core.ColorChanger;
import colorpicker.core.ColorFilter;
import colorpicker.core.RGBColor;
import colorpicker.i18n.Lang;
import colorpicker.settings.AppSettings;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.TexturePaint;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/**
 * On-screen colour picker (port of {@code Form2}, with the magnifier of the
 * unused {@code ColorPickerCursor} design).
 *
 * <p>The original laid an almost invisible top-most form over the live
 * desktop and read single pixels with {@code CopyFromScreen}. Here the whole
 * virtual desktop is captured once with {@link Robot} (at full device
 * resolution on HiDPI screens) and shown frozen, 1:1, in an undecorated
 * always-on-top dialog, so it looks like the desktop, ColorPicker's own
 * window included. A loupe next to the pipette shows the magnified pixels,
 * the colour before picking, the colour under the cursor and its hex code.
 *
 * <ul>
 * <li>Left button down: the colour under the cursor is applied
 * ({@code Form2_MouseDown}); while it is held the colour follows the cursor
 * live ({@code Form2_MouseMove} / {@code Timer1_Tick}).</li>
 * <li>Left button up: final colour, copied to the clipboard as
 * {@code #RRGGBB} when the "copy hex" setting is on, picker closed
 * ({@code Form2_MouseUp}).</li>
 * <li>Right button up: closes; if the colour was changed while the left
 * button was held, asks whether to revert to the original colour.</li>
 * <li>Escape closes without reverting ({@code Form2_KeyDown}).</li>
 * </ul>
 * Only one picker runs at a time ({@code Form1.colorPickerIsOn}). Everything
 * runs on the event dispatch thread except the capture itself.
 */
public final class ScreenColorPicker {

    /** Fallback when the PICKER:CAPTUREERROR translation is missing. */
    static final String CAPTURE_ERROR = "Unable to capture the screen.\n"
            + "The desktop session does not allow screen capture,\n"
            + "so the on-screen color picker cannot run.";

    /** Time given to the window manager / compositor to show the released button. */
    private static final int SETTLE_DELAY_MS = 50;

    private static ScreenColorPicker active;
    private static Cursor upsideDownPipette;

    private final MainWindow owner;
    private final RGBColor firstRGB;
    /** {@code Form2.firstColor}: the colour when the picker started. */
    private final Color firstColor;
    private final Cursor ownerCursor;
    private final KeyEventDispatcher escapeKey = this::dispatchKey;
    private Capture capture;
    private Overlay overlay;
    /** {@code Form2.curColor}: the last colour given to the colour manager. */
    private Color lastApplied;
    private boolean clicked;
    private boolean cancel;
    private boolean closed;

    ScreenColorPicker(MainWindow owner) {
        this.owner = owner;
        this.firstRGB = App.colorManager().getRGB();
        this.firstColor = App.colorManager().getColor();
        this.ownerCursor = owner != null && owner.isCursorSet() ? owner.getCursor() : null;
    }

    /**
     * Starts picking a colour from the screen ({@code Form1.colorPickerBtn_Click}).
     * Does nothing but bring the running picker to front if one is active.
     */
    public static void start(MainWindow owner) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> start(owner));
            return;
        }
        if (active != null) {
            active.toFront();
            return;
        }
        active = new ScreenColorPicker(owner != null ? owner : App.mainWindow());
        active.begin();
    }

    private void begin() {
        if (owner != null) {
            owner.setCursor(CursorFactory.pipetteCursor());
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escapeKey);
        // The button that started us repaints itself after its action listeners:
        // paint it now so the capture shows it released.
        SwingUtilities.invokeLater(() -> {
            if (closed) {
                return;
            }
            RepaintManager.currentManager(owner).paintDirtyRegions();
            Toolkit.getDefaultToolkit().sync();
            new CaptureWorker().execute();
        });
    }

    private void toFront() {
        if (overlay != null) {
            overlay.toFront();
            overlay.requestFocus();
        }
    }

    private void showOverlay(Capture c) {
        capture = c;
        try {
            PickerView view = new PickerView(c);
            overlay = new Overlay(owner, view);
            overlay.setVisible(true);
            overlay.toFront();
            view.requestFocusInWindow();
            view.syncOrigin();
            view.showAtPointer();
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    private void captureFailed() {
        close();
        String message = Lang.get("PICKER", "CAPTUREERROR");
        JOptionPane.showMessageDialog(owner, message.isEmpty() ? CAPTURE_ERROR : message, Lang.get("COMMON", "ERROR"),
                JOptionPane.ERROR_MESSAGE);
    }

    /** Left button pressed ({@code Form2_MouseDown}). */
    void leftPressed(Color c) {
        if (clicked || cancel || closed) {
            return;
        }
        clicked = true;
        apply(c);
    }

    /** Mouse moved with the left button held ({@code Form2_MouseMove}, {@code Timer1_Tick}). */
    void leftDragged(Color c) {
        if (cancel || closed) {
            return;
        }
        clicked = true;
        if (!c.equals(lastApplied)) {
            apply(c);
        }
    }

    /** Left button released: final colour, clipboard, close ({@code Form2_MouseUp}). */
    void leftReleased(Color c) {
        if (cancel || closed) {
            return;
        }
        clicked = false;
        if (!c.equals(lastApplied)) {
            apply(c);
        }
        if (AppSettings.get().isClipCopy()) {
            UiUtil.setClipboardText(ColorChanger.toHtml(c));
        }
        close();
    }

    /** Right button released: optionally revert, then close ({@code Form2_MouseUp}). */
    void rightReleased() {
        if (cancel || closed) {
            return;
        }
        if (clicked && lastApplied != null && (lastApplied.getRGB() & 0xFFFFFF) != (firstColor.getRGB() & 0xFFFFFF)) {
            cancel = true;
            if (overlay != null) {
                overlay.setAlwaysOnTop(false);
                overlay.setVisible(false);
            }
            Object[] options = {Lang.get("COMMON", "YES"), Lang.get("COMMON", "NO")};
            int answer = JOptionPane.showOptionDialog(owner, Lang.get("PICKER", "COLORCHANGED"), Lang.get("INFO"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
            if (answer == 0) {
                App.colorManager().setRGB(firstRGB);
            }
        }
        close();
    }

    private void apply(Color c) {
        lastApplied = c;
        App.colorManager().setRGB(c);
    }

    private boolean dispatchKey(KeyEvent e) {
        if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && !cancel && !closed) {
            close();
            return true;
        }
        return false;
    }

    /** Ends the session and releases the screenshot ({@code Form2_FormClosing}). Idempotent. */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escapeKey);
        if (overlay != null) {
            Overlay o = overlay;
            overlay = null;
            o.dispose();
        }
        if (capture != null) {
            capture.flush();
            capture = null;
        }
        if (owner != null) {
            owner.setCursor(ownerCursor);
        }
        if (active == this) {
            active = null;
        }
    }

    static boolean isWaylandSession() {
        String type = System.getenv("XDG_SESSION_TYPE");
        String display = System.getenv("WAYLAND_DISPLAY");
        return "wayland".equalsIgnoreCase(type) || (display != null && !display.isBlank());
    }

    private static synchronized Cursor upsideDownPipetteCursor() {
        if (upsideDownPipette == null) {
            // Hot spot of the flipped layout of ColorPickerCursor.GenerateColorPickerCursor.
            upsideDownPipette = CursorFactory.imageToCursor(Resources.image("pipette_upsidedown"), 22, 8,
                    "pipette_upsidedown");
        }
        return upsideDownPipette;
    }

    /** Captures the screen off the event dispatch thread. */
    private final class CaptureWorker extends SwingWorker<Capture, Void> {

        @Override
        protected Capture doInBackground() throws Exception {
            Thread.sleep(SETTLE_DELAY_MS);
            Capture c = Capture.take();
            if (isWaylandSession() && c.isBlack()) {
                c.flush();
                throw new AWTException("Blank screen capture: the session does not allow it");
            }
            return c;
        }

        @Override
        protected void done() {
            Capture result;
            try {
                result = get();
            } catch (ExecutionException e) {
                System.err.println("ColorPicker: screen capture failed: " + e.getCause());
                if (!closed) {
                    captureFailed();
                }
                return;
            } catch (InterruptedException e) {
                close();
                return;
            }
            if (closed || (owner != null && !owner.isDisplayable())) {
                result.flush();
                close();
                return;
            }
            showOverlay(result);
        }
    }

    /** The undecorated, always-on-top full-screen window (the {@code Form2} form). */
    private final class Overlay extends JDialog {
        private static final long serialVersionUID = 1L;

        Overlay(Window ownerWindow, PickerView view) {
            super(ownerWindow, ModalityType.MODELESS);
            setUndecorated(true);
            setAlwaysOnTop(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setIconImages(Resources.appIcons());
            setTitle("ColorPicker");
            setContentPane(view);
            setBounds(view.capture.bounds);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentMoved(ComponentEvent e) {
                    // The window manager may place us elsewhere: keep the image 1:1 with the screen.
                    view.syncOrigin();
                }
            });
        }

        @Override
        public void dispose() {
            super.dispose();
            close();
        }
    }

    /**
     * Paints the frozen screenshot and the loupe, and turns mouse events into
     * picker actions. The loupe follows the layout of
     * {@code ColorPickerCursor.GenerateColorPickerCursor}: above-right of the
     * pipette, flipped below-left near the top edge.
     */
    final class PickerView extends JComponent {
        private static final long serialVersionUID = 1L;

        /** Screen pixels shown in the loupe per side (odd: the picked pixel is in the middle). */
        private static final int ZOOM_PIXELS = 13;
        private static final int CELL = 9;
        private static final int ZOOM_BOX = ZOOM_PIXELS * CELL + 2;
        private static final int SWATCH_WIDTH = 28;
        private static final int PAD = 6;
        private static final int GAP = 5;
        private static final int SHADOW = 3;
        private static final int ARC = 8;
        /** Distance between the hot spot and the loupe, clear of the 13 px pipette. */
        private static final int OFFSET_X = 12;
        private static final int OFFSET_Y = 16;
        /** Height of the pipette above its tip: closer to the top the flipped pipette is used. */
        private static final int PIPETTE_HEIGHT = 14;

        final Capture capture;
        private final Point origin;
        private final Font font;
        private final FontMetrics metrics;
        private final int loupeWidth;
        private final int loupeHeight;
        private Point hover;
        private Point hoverScreen;
        private Color hoverColor;
        private Rectangle loupeBounds;
        private boolean loupeBelow;
        private boolean upsideDown;

        PickerView(Capture capture) {
            this.capture = capture;
            this.origin = capture.bounds.getLocation();
            Font base = UIManager.getFont("Label.font");
            font = (base != null ? base : new Font(Font.DIALOG, Font.PLAIN, 12)).deriveFont(Font.BOLD);
            metrics = getFontMetrics(font);
            loupeWidth = PAD + SWATCH_WIDTH + GAP + ZOOM_BOX + PAD + SHADOW;
            loupeHeight = PAD + ZOOM_BOX + GAP + metrics.getHeight() + PAD + SHADOW;
            setOpaque(true);
            setFocusable(true);
            setCursor(CursorFactory.pipetteCursor());
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    track(e);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    track(e);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hideLoupe();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    track(e);
                    if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
                        leftDragged(hoverColor);
                    }
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    track(e);
                    requestFocusInWindow();
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        leftPressed(hoverColor);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        track(e);
                        leftReleased(hoverColor);
                    } else if (e.getButton() == MouseEvent.BUTTON3) {
                        rightReleased();
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        /** Reads the on-screen position of this component (only possible while showing). */
        void syncOrigin() {
            if (isShowing()) {
                Point p = getLocationOnScreen();
                if (!p.equals(origin)) {
                    origin.setLocation(p);
                    loupeBounds = null;
                    repaint();
                }
            }
        }

        /** Shows the loupe at the current pointer position, before the first mouse event. */
        void showAtPointer() {
            PointerInfo info;
            try {
                info = MouseInfo.getPointerInfo();
            } catch (HeadlessException | SecurityException e) {
                return;
            }
            if (info != null && hover == null) {
                Point p = info.getLocation();
                moveTo(p.x - origin.x, p.y - origin.y);
            }
        }

        private void track(MouseEvent e) {
            int ox = e.getXOnScreen() - e.getX();
            int oy = e.getYOnScreen() - e.getY();
            if (ox != origin.x || oy != origin.y) {
                origin.setLocation(ox, oy);
                loupeBounds = null;
                repaint();
            }
            moveTo(e.getX(), e.getY());
        }

        /** Moves the loupe to a point in component coordinates. */
        void moveTo(int x, int y) {
            int sx = origin.x + x;
            int sy = origin.y + y;
            hover = new Point(x, y);
            hoverScreen = new Point(sx, sy);
            hoverColor = capture.colorAt(sx, sy);
            Rectangle monitor = new Rectangle(capture.monitorAt(sx, sy));
            boolean flipCursor = sy - monitor.y < PIPETTE_HEIGHT;
            monitor.translate(-origin.x, -origin.y);
            Rectangle old = loupeBounds;
            loupeBounds = layoutLoupe(x, y, monitor);
            if (old != null) {
                repaint(old);
            }
            repaint(loupeBounds);
            if (flipCursor != upsideDown) {
                upsideDown = flipCursor;
                setCursor(flipCursor ? upsideDownPipetteCursor() : CursorFactory.pipetteCursor());
            }
        }

        private void hideLoupe() {
            if (loupeBounds != null) {
                repaint(loupeBounds);
            }
            hover = null;
            loupeBounds = null;
        }

        private Rectangle layoutLoupe(int x, int y, Rectangle monitor) {
            int w = loupeWidth;
            int h = loupeHeight;
            int lx = x + OFFSET_X;
            int ly = y - OFFSET_Y - h;
            loupeBelow = ly < monitor.y;
            if (loupeBelow) {
                lx = x - OFFSET_X - w;
                ly = y + OFFSET_Y;
            }
            if (lx + w > monitor.x + monitor.width) {
                lx = x - OFFSET_X - w;
            }
            if (lx < monitor.x) {
                lx = x + OFFSET_X;
            }
            lx = Math.max(monitor.x, Math.min(lx, monitor.x + monitor.width - w));
            ly = Math.max(monitor.y, Math.min(ly, monitor.y + monitor.height - h));
            return new Rectangle(lx, ly, w, h);
        }

        /** The colour under the pointer, {@code null} before the first mouse event. */
        Color hoverColor() {
            return hoverColor;
        }

        Rectangle loupeBounds() {
            return loupeBounds == null ? null : new Rectangle(loupeBounds);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                Rectangle clip = g2.getClipBounds();
                if (clip == null) {
                    clip = new Rectangle(0, 0, getWidth(), getHeight());
                }
                Rectangle image = new Rectangle(capture.bounds);
                image.translate(-origin.x, -origin.y);
                if (!image.contains(clip)) {
                    g2.setColor(Color.BLACK);
                    g2.fill(clip);
                }
                capture.paint(g2, image.x, image.y);
                if (hover != null && loupeBounds != null && loupeBounds.intersects(clip)) {
                    paintLoupe(g2);
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintLoupe(Graphics2D g) {
            Rectangle r = loupeBounds;
            int x = r.x;
            int y = r.y;
            int w = r.width - SHADOW;
            int h = r.height - SHADOW;

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillRoundRect(x + SHADOW, y + SHADOW, w, h, ARC, ARC);
            g.setColor(UiUtil.controlBackground());
            g.fillRoundRect(x, y, w, h, ARC, ARC);
            g.setColor(Color.DARK_GRAY);
            g.drawRoundRect(x, y, w - 1, h - 1, ARC, ARC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

            // Magnified pixels around the hot spot (CreateZoomSpot).
            int zx = x + PAD + SWATCH_WIDTH + GAP;
            int zy = y + PAD;
            int cx = capture.pixelX(hoverScreen.x);
            int cy = capture.pixelY(hoverScreen.y);
            int half = ZOOM_PIXELS / 2;
            TexturePaint outside = null;
            for (int j = 0; j < ZOOM_PIXELS; j++) {
                for (int i = 0; i < ZOOM_PIXELS; i++) {
                    int rgb = capture.rgb(cx + i - half, cy + j - half);
                    if (rgb < 0) {
                        if (outside == null) {
                            BufferedImage board = Resources.image("checkboard");
                            outside = new TexturePaint(board,
                                    new Rectangle(zx, zy, board.getWidth(), board.getHeight()));
                        }
                        g.setPaint(outside);
                    } else {
                        g.setColor(new Color(rgb));
                    }
                    g.fillRect(zx + 1 + i * CELL, zy + 1 + j * CELL, CELL, CELL);
                }
            }
            int px = zx + 1 + half * CELL;
            int py = zy + 1 + half * CELL;
            g.setColor(Color.RED);
            g.drawRect(px - 1, py - 1, CELL + 1, CELL + 1);
            g.setColor(ColorFilter.NEGATIVE.apply(hoverColor));
            g.drawRect(px, py, CELL - 1, CELL - 1);
            g.setColor(Color.BLACK);
            g.drawRect(zx, zy, ZOOM_BOX - 1, ZOOM_BOX - 1);

            // The colour before picking and the pixel under the cursor, the latter nearest to the cursor.
            int swatchHeight = (ZOOM_BOX - GAP) / 2;
            Color top = loupeBelow ? hoverColor : firstColor;
            Color bottom = loupeBelow ? firstColor : hoverColor;
            paintSwatch(g, x + PAD, zy, swatchHeight, top);
            paintSwatch(g, x + PAD, zy + ZOOM_BOX - swatchHeight, swatchHeight, bottom);

            Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (hints instanceof Map<?, ?>) {
                g.addRenderingHints((Map<?, ?>) hints);
            } else {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            String hex = ColorChanger.toHtml(hoverColor);
            g.setFont(font);
            g.setColor(UiUtil.controlText());
            g.drawString(hex, x + (w - metrics.stringWidth(hex)) / 2, zy + ZOOM_BOX + GAP + metrics.getAscent());
        }

        private void paintSwatch(Graphics2D g, int x, int y, int height, Color c) {
            g.setColor(c);
            g.fillRect(x, y, SWATCH_WIDTH, height);
            g.setColor(Color.BLACK);
            g.drawRect(x, y, SWATCH_WIDTH - 1, height - 1);
        }
    }

    /**
     * A frozen image of the virtual desktop (all monitors). Coordinates given
     * to it are screen coordinates in user space; the image may have more
     * pixels than that on HiDPI screens.
     */
    static final class Capture {
        /** The virtual desktop, user space. */
        final Rectangle bounds;
        private final List<Rectangle> monitors;
        private final double scaleX;
        private final double scaleY;
        private BufferedImage image;

        Capture(BufferedImage image, Rectangle bounds, List<Rectangle> monitors) {
            this.image = image;
            this.bounds = new Rectangle(bounds);
            this.monitors = List.copyOf(monitors);
            this.scaleX = image.getWidth() / (double) bounds.width;
            this.scaleY = image.getHeight() / (double) bounds.height;
        }

        /** Captures every monitor with the highest resolution available. */
        static Capture take() throws AWTException {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            List<Rectangle> monitors = new ArrayList<>();
            Rectangle virtual = null;
            for (GraphicsDevice device : ge.getScreenDevices()) {
                Rectangle b = device.getDefaultConfiguration().getBounds();
                monitors.add(b);
                virtual = virtual == null ? new Rectangle(b) : virtual.union(b);
            }
            if (virtual == null || virtual.isEmpty()) {
                throw new AWTException("No screen to capture");
            }
            Robot robot = new Robot();
            MultiResolutionImage shot = robot.createMultiResolutionScreenCapture(virtual);
            return new Capture(highestResolution(shot), virtual, monitors);
        }

        private static BufferedImage highestResolution(MultiResolutionImage shot) {
            List<Image> variants = shot.getResolutionVariants();
            Image best = null;
            long bestArea = -1;
            for (Image v : variants) {
                long area = (long) v.getWidth(null) * v.getHeight(null);
                if (area > bestArea) {
                    best = v;
                    bestArea = area;
                }
            }
            for (Image v : variants) {
                if (v != best) {
                    v.flush();
                }
            }
            if (best instanceof BufferedImage) {
                return (BufferedImage) best;
            }
            BufferedImage copy = new BufferedImage(best.getWidth(null), best.getHeight(null),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(best, 0, 0, null);
            g.dispose();
            best.flush();
            return copy;
        }

        /** Image column of a screen x coordinate (clamped to the image). */
        int pixelX(int screenX) {
            int px = (int) Math.floor((screenX - bounds.x) * scaleX);
            return Math.max(0, Math.min(px, width() - 1));
        }

        /** Image row of a screen y coordinate (clamped to the image). */
        int pixelY(int screenY) {
            int py = (int) Math.floor((screenY - bounds.y) * scaleY);
            return Math.max(0, Math.min(py, height() - 1));
        }

        /** {@code 0xRRGGBB} of an image pixel, or -1 outside the image. */
        int rgb(int px, int py) {
            BufferedImage img = image;
            if (img == null || px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) {
                return -1;
            }
            return img.getRGB(px, py) & 0xFFFFFF;
        }

        /** The opaque colour at a screen position. */
        Color colorAt(int screenX, int screenY) {
            int rgb = rgb(pixelX(screenX), pixelY(screenY));
            return new Color(Math.max(rgb, 0));
        }

        /** Bounds of the monitor showing a screen position (the virtual desktop if none). */
        Rectangle monitorAt(int screenX, int screenY) {
            for (Rectangle m : monitors) {
                if (m.contains(screenX, screenY)) {
                    return m;
                }
            }
            return bounds;
        }

        /** {@code true} when every pixel is black (what a session that blocks capture returns). */
        boolean isBlack() {
            BufferedImage img = image;
            if (img == null) {
                return true;
            }
            int w = img.getWidth();
            int[] row = new int[w];
            for (int y = 0; y < img.getHeight(); y++) {
                img.getRGB(0, y, w, 1, row, 0, w);
                for (int v : row) {
                    if ((v & 0xFFFFFF) != 0) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Draws the image 1:1 on the screen, its top-left corner at (x, y). */
        void paint(Graphics2D g, int x, int y) {
            BufferedImage img = image;
            if (img == null) {
                return;
            }
            if (img.getWidth() == bounds.width && img.getHeight() == bounds.height) {
                g.drawImage(img, x, y, null);
            } else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g.drawImage(img, x, y, bounds.width, bounds.height, null);
            }
        }

        private int width() {
            BufferedImage img = image;
            return img == null ? 1 : img.getWidth();
        }

        private int height() {
            BufferedImage img = image;
            return img == null ? 1 : img.getHeight();
        }

        /** Releases the image memory. */
        void flush() {
            BufferedImage img = image;
            image = null;
            if (img != null) {
                img.flush();
            }
        }
    }
}
