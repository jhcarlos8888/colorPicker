package colorpicker.settings;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/**
 * User settings (port of {@code My.Settings}), stored in
 * {@code $XDG_CONFIG_HOME/colorpicker/settings.properties}
 * (normally {@code ~/.config/colorpicker/settings.properties}).
 *
 * <p>Same keys and defaults as the original {@code Settings.settings}.
 */
public final class AppSettings {

    private static final String OPTIMIZE_SPEED = "optimizeSpeed";
    private static final String LANGUAGE = "language";
    private static final String FIRST_TIME = "firstTime";
    private static final String USE_HSV = "useHSV";
    private static final String USE_RGB = "useRGB";
    private static final String CLIP_COPY = "clipCopy";

    private static AppSettings instance;

    private final Path file;
    private boolean optimizeSpeed = true;
    private String language = "en";
    private boolean firstTime = true;
    private boolean useHSV = true;
    private boolean useRGB = true;
    private boolean clipCopy = false;

    AppSettings(Path file) {
        this.file = file;
        load();
    }

    /** The settings of the current user, loaded on first access. */
    public static synchronized AppSettings get() {
        if (instance == null) {
            instance = new AppSettings(configDirectory().resolve("settings.properties"));
        }
        return instance;
    }

    /** {@code $XDG_CONFIG_HOME/colorpicker}, defaulting to {@code ~/.config/colorpicker}. */
    public static Path configDirectory() {
        String xdg = System.getenv("XDG_CONFIG_HOME");
        Path base = xdg != null && !xdg.isBlank()
                ? Paths.get(xdg)
                : Paths.get(System.getProperty("user.home"), ".config");
        return base.resolve("colorpicker");
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("ColorPicker: could not read settings " + file + ": " + e);
            return;
        }
        optimizeSpeed = bool(p, OPTIMIZE_SPEED, optimizeSpeed);
        language = p.getProperty(LANGUAGE, language).trim();
        firstTime = bool(p, FIRST_TIME, firstTime);
        useHSV = bool(p, USE_HSV, useHSV);
        useRGB = bool(p, USE_RGB, useRGB);
        clipCopy = bool(p, CLIP_COPY, clipCopy);
        if (!useHSV && !useRGB) {
            // At least one colour space tab must stay visible.
            useRGB = true;
        }
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    /** Writes the settings to disk; errors are reported on stderr only. */
    public synchronized void save() {
        Properties p = new Properties();
        p.setProperty(OPTIMIZE_SPEED, Boolean.toString(optimizeSpeed));
        p.setProperty(LANGUAGE, language);
        p.setProperty(FIRST_TIME, Boolean.toString(firstTime));
        p.setProperty(USE_HSV, Boolean.toString(useHSV));
        p.setProperty(USE_RGB, Boolean.toString(useRGB));
        p.setProperty(CLIP_COPY, Boolean.toString(clipCopy));
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp)) {
                p.store(out, "ColorPicker settings");
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("ColorPicker: could not save settings " + file + ": " + e);
        }
    }

    public synchronized boolean isOptimizeSpeed() {
        return optimizeSpeed;
    }

    public synchronized void setOptimizeSpeed(boolean optimizeSpeed) {
        this.optimizeSpeed = optimizeSpeed;
    }

    /** Two-letter language code ({@code en}, {@code fr}, {@code es}, {@code nb}). */
    public synchronized String getLanguage() {
        return language;
    }

    public synchronized void setLanguage(String language) {
        this.language = language;
    }

    public synchronized boolean isFirstTime() {
        return firstTime;
    }

    public synchronized void setFirstTime(boolean firstTime) {
        this.firstTime = firstTime;
    }

    public synchronized boolean isUseHSV() {
        return useHSV;
    }

    public synchronized void setUseHSV(boolean useHSV) {
        this.useHSV = useHSV;
    }

    public synchronized boolean isUseRGB() {
        return useRGB;
    }

    public synchronized void setUseRGB(boolean useRGB) {
        this.useRGB = useRGB;
    }

    /** "Copy Hex to clipboard after selection" (screen colour picker). */
    public synchronized boolean isClipCopy() {
        return clipCopy;
    }

    public synchronized void setClipCopy(boolean clipCopy) {
        this.clipCopy = clipCopy;
    }
}
