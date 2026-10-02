package colorpicker.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link AppSettings} (port of {@code My.Settings} / Settings.settings). */
class AppSettingsTest {

    @TempDir
    Path dir;

    /** Defaults of the original Settings.settings. */
    private static void assertDefaults(AppSettings s) {
        assertTrue(s.isOptimizeSpeed());
        assertEquals("en", s.getLanguage());
        assertTrue(s.isFirstTime());
        assertTrue(s.isUseHSV());
        assertTrue(s.isUseRGB());
        assertFalse(s.isClipCopy());
    }

    @Test
    void missingFileGivesDefaults() {
        assertDefaults(new AppSettings(dir.resolve("settings.properties")));
    }

    @Test
    void saveAndLoadRoundTrip() {
        Path file = dir.resolve("a").resolve("b").resolve("settings.properties");
        AppSettings s = new AppSettings(file);
        s.setOptimizeSpeed(false);
        s.setLanguage("nb");
        s.setFirstTime(false);
        s.setUseHSV(false);
        s.setUseRGB(true);
        s.setClipCopy(true);
        s.save();

        assertTrue(Files.isRegularFile(file), "parent directories are created");
        assertFalse(Files.exists(file.resolveSibling("settings.properties.tmp")), "temporary file left behind");

        AppSettings loaded = new AppSettings(file);
        assertFalse(loaded.isOptimizeSpeed());
        assertEquals("nb", loaded.getLanguage());
        assertFalse(loaded.isFirstTime());
        assertFalse(loaded.isUseHSV());
        assertTrue(loaded.isUseRGB());
        assertTrue(loaded.isClipCopy());
    }

    @Test
    void secondRoundTripKeepsHsvOnly() {
        Path file = dir.resolve("settings.properties");
        AppSettings s = new AppSettings(file);
        s.setUseRGB(false);
        s.setUseHSV(true);
        s.setLanguage("fr");
        s.save();
        s.setLanguage("es");
        s.save();

        AppSettings loaded = new AppSettings(file);
        assertFalse(loaded.isUseRGB());
        assertTrue(loaded.isUseHSV());
        assertEquals("es", loaded.getLanguage());
    }

    @Test
    void savedFileUsesOriginalKeys() throws IOException {
        Path file = dir.resolve("settings.properties");
        AppSettings s = new AppSettings(file);
        s.setClipCopy(true);
        s.save();
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            p.load(in);
        }
        assertEquals("true", p.getProperty("optimizeSpeed"));
        assertEquals("en", p.getProperty("language"));
        assertEquals("true", p.getProperty("firstTime"));
        assertEquals("true", p.getProperty("useHSV"));
        assertEquals("true", p.getProperty("useRGB"));
        assertEquals("true", p.getProperty("clipCopy"));
    }

    @Test
    void bothColorSpacesOffForcesRgbOn() {
        Path file = dir.resolve("settings.properties");
        AppSettings s = new AppSettings(file);
        s.setUseRGB(false);
        s.setUseHSV(false);
        s.save();

        AppSettings loaded = new AppSettings(file);
        assertTrue(loaded.isUseRGB());
        assertFalse(loaded.isUseHSV());
    }

    @Test
    void handWrittenFileIsTrimmedAndCaseInsensitive() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.writeString(file, "language =  fr  \nclipCopy=TRUE\noptimizeSpeed = False \n", StandardCharsets.ISO_8859_1);
        AppSettings s = new AppSettings(file);
        assertEquals("fr", s.getLanguage());
        assertTrue(s.isClipCopy());
        assertFalse(s.isOptimizeSpeed());
        // keys not present keep their defaults
        assertTrue(s.isFirstTime());
        assertTrue(s.isUseHSV());
        assertTrue(s.isUseRGB());
    }

    @Test
    void corruptFileKeepsDefaults() throws IOException {
        Path file = dir.resolve("settings.properties");
        // A malformed \\uxxxx escape makes Properties.load throw.
        Files.writeString(file, "optimizeSpeed=false\nlanguage=\\uZZZZ\nclipCopy=true\n", StandardCharsets.ISO_8859_1);
        assertDefaults(new AppSettings(file));
    }

    @Test
    void unrelatedContentKeepsDefaults() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.write(file, new byte[] {0, 1, 2, (byte) 0xFF, (byte) 0xFE, 10, 13});
        assertDefaults(new AppSettings(file));
    }

    @Test
    void directoryInsteadOfFileKeepsDefaults() throws IOException {
        Path file = dir.resolve("settings.properties");
        Files.createDirectory(file);
        AppSettings s = new AppSettings(file);
        assertDefaults(s);
        // saving fails quietly (reported on stderr) instead of throwing
        s.setLanguage("fr");
        s.save();
        assertTrue(Files.isDirectory(file));
    }

    @Test
    void configDirectoryEndsWithApplicationName() {
        Path config = AppSettings.configDirectory();
        assertEquals("colorpicker", config.getFileName().toString());
    }
}
