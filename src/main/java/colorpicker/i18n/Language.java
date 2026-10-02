package colorpicker.i18n;

import java.util.Locale;

/**
 * The bundled languages, in the order of the original language combo box.
 */
public enum Language {
    ENGLISH("en", "English", "lang_English.txt"),
    FRENCH("fr", "Français", "lang_French.txt"),
    SPANISH("es", "Español", "lang_Spanish.txt"),
    NORWEGIAN("nb", "Norsk", "lang_Norwegian.txt");

    private final String code;
    private final String displayName;
    private final String resourceName;

    Language(String code, String displayName, String resourceName) {
        this.code = code;
        this.displayName = displayName;
        this.resourceName = resourceName;
    }

    /** Two-letter code stored in the settings ({@code en}, {@code fr}, {@code es}, {@code nb}). */
    public String code() {
        return code;
    }

    /** Name shown in the language combo box. */
    public String displayName() {
        return displayName;
    }

    /** Classpath resource holding the language file. */
    public String resourcePath() {
        return "/colorpicker/lang/" + resourceName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /** @return the language for a settings code, or {@code null} if unknown */
    public static Language fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (Language l : values()) {
            if (l.code.equalsIgnoreCase(code)) {
                return l;
            }
        }
        return null;
    }

    /** @return the language shown under this combo box name, or {@code null} */
    public static Language fromDisplayName(String name) {
        for (Language l : values()) {
            if (l.displayName.equals(name)) {
                return l;
            }
        }
        return null;
    }

    /**
     * Language to use on first start, from the system locale (the original
     * used {@code InstalledUICulture.TwoLetterISOLanguageName}). Norwegian
     * variants ({@code no}, {@code nb}, {@code nn}) map to Norsk; anything not
     * bundled falls back to English.
     */
    public static Language fromLocale(Locale locale) {
        String lang = locale == null ? "" : locale.getLanguage();
        if (lang.equals("no") || lang.equals("nn")) {
            return NORWEGIAN;
        }
        Language l = fromCode(lang);
        return l == null ? ENGLISH : l;
    }
}
