package colorpicker.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for {@link Lang} and for the completeness of the bundled language
 * files.
 */
class LangTest {

    /** Keys the original files already lacked: kept as they were. */
    private static final Set<String> ALLOWED_MISSING = Set.of("TOOLS:ADVANCEDGRAY");
    private static final Set<String> ALLOWED_EXTRA = Set.of("TOOL_DESC:ADVANCEDGRAY");

    /** {@code Lang.get("KEY")} or {@code Lang.get("GROUP", "KEY")} with literal arguments. */
    private static final Pattern LANG_CALL = Pattern.compile(
            "\\bLang\\s*\\.\\s*get\\s*\\(\\s*\"([^\"\\\\]*)\"\\s*(?:,\\s*\"([^\"\\\\]*)\"\\s*)?\\)");

    @AfterEach
    void restoreEnglish() {
        Lang.load(Language.ENGLISH);
    }

    @Test
    void getReturnsTranslation() {
        Lang.load(Language.FRENCH);
        assertSame(Language.FRENCH, Lang.current());
        assertEquals(Lang.provider().getKey("SETTINGS", "LANGUAGE"), Lang.get("SETTINGS", "LANGUAGE"));
        Lang.load(Language.ENGLISH);
        assertEquals("Language", Lang.get("SETTINGS", "LANGUAGE"));
        assertEquals("Filters", Lang.get("FILTERS"));
        assertEquals("Advanced\nrandom color", Lang.get("TOOLS", "ADVANCEDRANDOM"));
    }

    @Test
    void missingKeyFallsBackToEnglishThenEmpty() {
        Lang.load(Language.SPANISH);
        assertEquals("Advanced grayscale", Lang.get("TOOLS", "ADVANCEDGRAY"));
        assertEquals("", Lang.get("NO_SUCH_KEY"));
        assertEquals("", Lang.get("NO_SUCH_GROUP", "KEY"));
    }

    @Test
    void listenersAreNotifiedAfterLoad() {
        List<String> seen = new ArrayList<>();
        Runnable l = () -> seen.add(Lang.get("SETTINGS", "LANGUAGESET"));
        Lang.addListener(l);
        try {
            Lang.load(Language.ENGLISH);
            Lang.load(Language.NORWEGIAN);
        } finally {
            Lang.removeListener(l);
        }
        Lang.load(Language.FRENCH);
        assertEquals(2, seen.size());
        assertEquals("Set", seen.get(0));
        assertEquals(parseKeys(Language.NORWEGIAN).get("SETTINGS:LANGUAGESET"), seen.get(1));
    }

    /** Every literal key used by the Java sources exists in the English file. */
    @Test
    void everyKeyUsedInSourcesExistsInEnglish() throws IOException {
        Path sources = Paths.get("src", "main", "java");
        assertTrue(Files.isDirectory(sources), "run the tests from the project directory: " + sources.toAbsolutePath());
        Map<String, String> english = parseKeys(Language.ENGLISH);
        Set<String> used = new TreeSet<>();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                Matcher m = LANG_CALL.matcher(code);
                while (m.find()) {
                    String key = m.group(2) == null ? m.group(1) : m.group(1) + ":" + m.group(2);
                    used.add(key);
                    if (!english.containsKey(key)) {
                        missing.add(key + " (" + sources.relativize(file) + ")");
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "keys missing in lang_English.txt: " + missing);
        // Once the UI is ported these are certainly used; before that the scan finds nothing.
        if (!used.isEmpty()) {
            Lang.load(Language.ENGLISH);
            for (String key : used) {
                assertFalse(Lang.get(key).isEmpty(), key);
            }
        }
    }

    @Test
    void keyScannerRecognisesLiteralCallsOnly() {
        List<String> found = new ArrayList<>();
        Matcher m = LANG_CALL.matcher("a(Lang.get(\"A\")); b(Lang.get( \"G\" ,\n \"K\" )); c(Lang.get(\"RGB\", key));"
                + " d(Lang.get(group, \"X\")); e(MyLang.get(\"NO\"));");
        while (m.find()) {
            found.add(m.group(2) == null ? m.group(1) : m.group(1) + ":" + m.group(2));
        }
        assertEquals(List.of("A", "G:K"), found);
    }

    @ParameterizedTest
    @EnumSource(value = Language.class, names = {"FRENCH", "SPANISH", "NORWEGIAN"})
    void everyEnglishKeyIsTranslated(Language language) {
        Map<String, String> english = parseKeys(Language.ENGLISH);
        Map<String, String> other = parseKeys(language);

        Set<String> missing = new TreeSet<>(english.keySet());
        missing.removeAll(other.keySet());
        missing.removeAll(ALLOWED_MISSING);
        assertTrue(missing.isEmpty(), language + " lacks " + missing);

        Set<String> extra = new TreeSet<>(other.keySet());
        extra.removeAll(english.keySet());
        extra.removeAll(ALLOWED_EXTRA);
        assertTrue(extra.isEmpty(), language + " has unknown keys " + extra);
    }

    /** The parser sees exactly the keys a plain line scan finds (no line silently lost). */
    @ParameterizedTest
    @EnumSource(Language.class)
    void providerLoadsEveryKey(Language language) {
        Map<String, String> keys = parseKeys(language);
        assertTrue(keys.size() > 100, "suspiciously few keys: " + keys.size());
        LanguageProvider p = new LanguageProvider();
        p.openFile(Lang.readResource(language));
        for (Map.Entry<String, String> e : keys.entrySet()) {
            String value = p.getKey(e.getKey());
            assertNotNull(value, e.getKey());
            assertEquals(e.getValue().replace("\\n", "\n"), value, e.getKey());
            assertFalse(value.contains("\r"), e.getKey());
        }
        assertFalse(p.getInfo("name").isEmpty());
        assertFalse(p.getInfo("author").isEmpty());
    }

    /**
     * Independent minimal reader: {@code NAME=VALUE} lines with a non-empty
     * value, skipping comments and {@code #info} lines.
     */
    private static Map<String, String> parseKeys(Language language) {
        String content = Lang.readResource(language);
        if (content.startsWith("﻿")) {
            content = content.substring(1);
        }
        Map<String, String> keys = new LinkedHashMap<>();
        for (String line : content.split("\r\n|\r|\n")) {
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq > 0 && eq < line.length() - 1) {
                keys.putIfAbsent(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return keys;
    }
}
