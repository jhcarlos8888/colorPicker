package colorpicker.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Loads the bundled images from {@code /colorpicker/images/} (port of
 * {@code My.Resources}). Available names (without {@code .png}):
 * {@code pipette}, {@code pipette_upsidedown}, {@code copy_cursor},
 * {@code websafecoloricon}, {@code websafecoloricongray}, {@code huegradient},
 * {@code checkboard}, {@code cuLogo}, {@code webColorBtn},
 * {@code colorPickerBtn}, {@code randomColorBtn}, {@code appicon},
 * {@code appicon_16} ... {@code appicon_128}.
 */
public final class Resources {

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final int[] ICON_SIZES = {16, 32, 48, 64, 128};

    private Resources() {
    }

    /** @param name image name without extension, e.g. {@code "pipette"} */
    public static BufferedImage image(String name) {
        return CACHE.computeIfAbsent(name, Resources::load);
    }

    public static ImageIcon icon(String name) {
        return new ImageIcon(image(name));
    }

    /** Application icons in several sizes, for {@code Window.setIconImages}. */
    public static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int s : ICON_SIZES) {
            icons.add(image("appicon_" + s));
        }
        return icons;
    }

    /** Application version, e.g. {@code 1.0.0}. */
    public static String version() {
        Properties p = new Properties();
        try (InputStream in = Resources.class.getResourceAsStream("/colorpicker/version.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            // fall through to the default below
        }
        String v = p.getProperty("version", "");
        return v.isEmpty() || v.startsWith("$") ? "1.0.0" : v;
    }

    private static BufferedImage load(String name) {
        String path = "/colorpicker/images/" + name + ".png";
        try (InputStream in = Resources.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing image resource " + path);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
