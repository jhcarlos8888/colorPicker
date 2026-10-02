package colorpicker.ui;

import colorpicker.core.ColorFilter;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;

/**
 * Custom mouse cursors (port of {@code ColorCursor} and
 * {@code ColorPickerCursor.ImageToCursor}).
 */
public final class CursorFactory {

    private static Cursor pipette;

    private CursorFactory() {
    }

    /** Pipette cursor of the screen colour picker, hot spot at (9, 22) like the original. */
    public static synchronized Cursor pipetteCursor() {
        if (pipette == null) {
            pipette = imageToCursor(Resources.image("pipette"), 9, 22, "pipette");
        }
        return pipette;
    }

    /**
     * Drag cursor: the "copy" arrow with a 15x15 swatch of the dragged colour,
     * outlined with its negative, in the top-right corner.
     */
    public static Cursor colorCursor(Color c) {
        return imageToCursor(colorCursorImage(c), 0, 0, "color-" + Integer.toHexString(c.getRGB()));
    }

    /** The image used by {@link #colorCursor(Color)} (32x32, hot spot 0,0). */
    public static BufferedImage colorCursorImage(Color c) {
        Color opaque = new Color(c.getRed(), c.getGreen(), c.getBlue());
        Color neg = ColorFilter.NEGATIVE.apply(opaque);
        BufferedImage bmp = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bmp.createGraphics();
        try {
            g.setColor(opaque);
            g.fillRect(16, 0, 15, 15);
            g.setColor(neg);
            g.drawRect(16, 0, 15, 15);
            g.drawImage(Resources.image("copy_cursor"), 0, 0, 32, 32, null);
        } finally {
            g.dispose();
        }
        return bmp;
    }

    /**
     * Creates a cursor from an image. The image is placed unscaled on a canvas
     * of the platform's best cursor size when that is larger (so the hot spot
     * stays exact), or scaled down with the hot spot when it is smaller.
     * Returns the default cursor in headless mode or if cursors are unsupported.
     */
    public static Cursor imageToCursor(BufferedImage img, int xHotspot, int yHotspot, String name) {
        if (GraphicsEnvironment.isHeadless()) {
            return Cursor.getDefaultCursor();
        }
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension best = tk.getBestCursorSize(img.getWidth(), img.getHeight());
        if (best == null || best.width <= 0 || best.height <= 0) {
            return Cursor.getDefaultCursor();
        }
        BufferedImage canvas;
        int hx = xHotspot;
        int hy = yHotspot;
        if (best.width >= img.getWidth() && best.height >= img.getHeight()) {
            canvas = new BufferedImage(best.width, best.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        } else {
            double sx = best.width / (double) img.getWidth();
            double sy = best.height / (double) img.getHeight();
            canvas = new BufferedImage(best.width, best.height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, best.width, best.height, null);
            g.dispose();
            hx = (int) Math.floor(xHotspot * sx);
            hy = (int) Math.floor(yHotspot * sy);
        }
        hx = Math.max(0, Math.min(hx, canvas.getWidth() - 1));
        hy = Math.max(0, Math.min(hy, canvas.getHeight() - 1));
        try {
            return tk.createCustomCursor(canvas, new Point(hx, hy), name);
        } catch (IndexOutOfBoundsException | java.awt.HeadlessException e) {
            return Cursor.getDefaultCursor();
        }
    }
}
