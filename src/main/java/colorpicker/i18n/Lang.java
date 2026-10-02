package colorpicker.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global access to the active translations (port of {@code LanguageModule.lang}).
 *
 * <p>Missing keys fall back to English and finally to an empty string (the
 * original showed an empty text).
 *
 * <p>Windows register a listener with {@link #addListener(Runnable)} to refresh
 * their texts when the language changes (the original called
 * {@code updateLang()} on every form) and must remove it when disposed.
 */
public final class Lang {

    private static final LanguageProvider PROVIDER = new LanguageProvider();
    private static final LanguageProvider ENGLISH = new LanguageProvider();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static Language current;

    static {
        ENGLISH.openFile(readResource(Language.ENGLISH));
        PROVIDER.addLanguageUpdatedListener(() -> {
            for (Runnable l : LISTENERS) {
                l.run();
            }
        });
    }

    private Lang() {
    }

    /** Loads a language and notifies every registered listener. */
    public static void load(Language language) {
        current = language;
        PROVIDER.openFile(readResource(language));
    }

    /** @return the loaded language, or {@code null} before {@link #load} */
    public static Language current() {
        return current;
    }

    public static LanguageProvider provider() {
        return PROVIDER;
    }

    /** {@code lang("KEY")}. */
    public static String get(String key) {
        String v = PROVIDER.getKey(key);
        if (v == null) {
            v = ENGLISH.getKey(key);
        }
        return v == null ? "" : v;
    }

    /** {@code lang("GROUP", "KEY")}. */
    public static String get(String group, String key) {
        String v = PROVIDER.getKey(group, key);
        if (v == null) {
            v = ENGLISH.getKey(group, key);
        }
        return v == null ? "" : v;
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    static String readResource(Language language) {
        try (InputStream in = Lang.class.getResourceAsStream(language.resourcePath())) {
            if (in == null) {
                throw new IllegalStateException("Missing language resource " + language.resourcePath());
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
