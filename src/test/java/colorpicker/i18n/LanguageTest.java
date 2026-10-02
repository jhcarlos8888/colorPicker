package colorpicker.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link Language} (VB {@code getLanguageRessource} / {@code returnShortLangString}...). */
class LanguageTest {

    @Test
    void comboBoxOrderAndNames() {
        // langCombo.Items: "English", "Français", "Español", "Norsk"
        assertEquals(List.of(Language.ENGLISH, Language.FRENCH, Language.SPANISH, Language.NORWEGIAN),
                List.of(Language.values()));
        assertEquals("English", Language.ENGLISH.displayName());
        assertEquals("Français", Language.FRENCH.displayName());
        assertEquals("Español", Language.SPANISH.displayName());
        assertEquals("Norsk", Language.NORWEGIAN.displayName());
        assertEquals("Norsk", Language.NORWEGIAN.toString());
    }

    @Test
    void codes() {
        assertEquals("en", Language.ENGLISH.code());
        assertEquals("fr", Language.FRENCH.code());
        assertEquals("es", Language.SPANISH.code());
        assertEquals("nb", Language.NORWEGIAN.code());
    }

    @Test
    void fromCode() {
        assertEquals(Language.ENGLISH, Language.fromCode("en"));
        assertEquals(Language.FRENCH, Language.fromCode("fr"));
        assertEquals(Language.SPANISH, Language.fromCode("es"));
        assertEquals(Language.NORWEGIAN, Language.fromCode("nb"));
        assertEquals(Language.FRENCH, Language.fromCode("FR"));
        assertNull(Language.fromCode("no"));
        assertNull(Language.fromCode("de"));
        assertNull(Language.fromCode(""));
        assertNull(Language.fromCode(null));
    }

    @Test
    void fromDisplayName() {
        assertEquals(Language.ENGLISH, Language.fromDisplayName("English"));
        assertEquals(Language.FRENCH, Language.fromDisplayName("Français"));
        assertEquals(Language.SPANISH, Language.fromDisplayName("Español"));
        assertEquals(Language.NORWEGIAN, Language.fromDisplayName("Norsk"));
        assertNull(Language.fromDisplayName("english"));
        assertNull(Language.fromDisplayName("Francais"));
        assertNull(Language.fromDisplayName(""));
        assertNull(Language.fromDisplayName(null));
    }

    @Test
    void fromLocale() {
        assertEquals(Language.NORWEGIAN, Language.fromLocale(new Locale("nb")));
        assertEquals(Language.NORWEGIAN, Language.fromLocale(new Locale("no")));
        assertEquals(Language.NORWEGIAN, Language.fromLocale(new Locale("nn")));
        assertEquals(Language.NORWEGIAN, Language.fromLocale(Locale.forLanguageTag("nb-NO")));
        assertEquals(Language.NORWEGIAN, Language.fromLocale(Locale.forLanguageTag("nn-NO")));
        assertEquals(Language.SPANISH, Language.fromLocale(new Locale("es")));
        assertEquals(Language.SPANISH, Language.fromLocale(Locale.forLanguageTag("es-CO")));
        assertEquals(Language.FRENCH, Language.fromLocale(Locale.FRANCE));
        assertEquals(Language.ENGLISH, Language.fromLocale(Locale.US));
        assertEquals(Language.ENGLISH, Language.fromLocale(Locale.GERMAN));
        assertEquals(Language.ENGLISH, Language.fromLocale(new Locale("de")));
        assertEquals(Language.ENGLISH, Language.fromLocale(Locale.JAPAN));
        assertEquals(Language.ENGLISH, Language.fromLocale(Locale.ROOT));
        assertEquals(Language.ENGLISH, Language.fromLocale(null));
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void resourceExists(Language language) {
        assertNotNull(Language.class.getResource(language.resourcePath()), language.resourcePath());
    }

    /** The {@code #shortname} of each bundled file is the settings code. */
    @ParameterizedTest
    @EnumSource(Language.class)
    void fileShortNameMatchesCode(Language language) {
        LanguageProvider p = new LanguageProvider();
        p.openFile(Lang.readResource(language));
        assertEquals(language.code(), p.getInfo("shortname"));
    }
}
