package colorpicker.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Parity tests for the language file parser (VB {@code LanguageProvider}). */
class LanguageProviderTest {

    private static LanguageProvider parse(String content) {
        LanguageProvider p = new LanguageProvider();
        p.openFile(content);
        return p;
    }

    @Test
    void simpleAndGroupKeys() {
        LanguageProvider p = parse("RGB=RGB\nRGB:R=Red\nTOOL_ADVRAND:GENERATE=Generate");
        assertEquals("RGB", p.getKey("RGB"));
        assertEquals("Red", p.getKey("RGB", "R"));
        assertEquals("Red", p.getKey("RGB:R"));
        assertEquals("Generate", p.getKey("TOOL_ADVRAND", "GENERATE"));
    }

    @Test
    void missingKeysGiveNull() {
        LanguageProvider p = parse("A=1\nG:K=2");
        assertNull(p.getKey("B"));
        assertNull(p.getKey("G", "X"));
        assertNull(p.getKey("X", "K"));
        assertNull(p.getKey("K"));
        assertNull(p.getKey("G"));
    }

    @Test
    void keysAreCaseSensitive() {
        LanguageProvider p = parse("RGB=x\nG:K=y");
        assertNull(p.getKey("rgb"));
        assertNull(p.getKey("g", "k"));
    }

    @Test
    void byteOrderMarkIsIgnored() {
        LanguageProvider p = parse("﻿FIRST=1\nSECOND=2");
        assertEquals("1", p.getKey("FIRST"));
        assertEquals("2", p.getKey("SECOND"));
    }

    @Test
    void byteOrderMarkBeforeInfoKey() {
        LanguageProvider p = parse("﻿#author=Example Author\n#name=English");
        assertEquals("Example Author", p.getInfo("author"));
        assertEquals("English", p.getInfo("name"));
    }

    @Test
    void crlfLfAndCrLineEndings() {
        // The original split on both CR and LF and dropped empty entries.
        LanguageProvider p = parse("A=1\r\nB=2\nC=3\rD=4\r\n\r\n\n\nE=5\r\n");
        assertEquals("1", p.getKey("A"));
        assertEquals("2", p.getKey("B"));
        assertEquals("3", p.getKey("C"));
        assertEquals("4", p.getKey("D"));
        assertEquals("5", p.getKey("E"));
    }

    @Test
    void commentsAreIgnored() {
        LanguageProvider p = parse("//A=1\n// comment\nB=2\n//G:K=3");
        assertNull(p.getKey("A"));
        assertNull(p.getKey("//A"));
        assertNull(p.getKey("G", "K"));
        assertEquals("2", p.getKey("B"));
    }

    @Test
    void emptyValuesAreIgnored() {
        LanguageProvider p = parse("A=\nG:K=\nB=x");
        assertNull(p.getKey("A"));
        assertNull(p.getKey("G", "K"));
        assertEquals("x", p.getKey("B"));
    }

    @Test
    void valueKeepsEverythingAfterTheFirstEquals() {
        LanguageProvider p = parse("A=b=c\nB= spaced \nC ==x");
        assertEquals("b=c", p.getKey("A"));
        assertEquals(" spaced ", p.getKey("B"));
        assertEquals("=x", p.getKey("C "));
        assertNull(p.getKey("C"));
    }

    @Test
    void backslashNBecomesNewLine() {
        LanguageProvider p = parse("TOOLS:ADVANCEDRANDOM=Advanced\\nrandom color\nX=a\\nb\\nc");
        assertEquals("Advanced\nrandom color", p.getKey("TOOLS", "ADVANCEDRANDOM"));
        assertEquals("a\nb\nc", p.getKey("X"));
    }

    @Test
    void infoKeys() {
        LanguageProvider p = parse("#author=Me\n#name=Test\n#shortname=tt\n#contact=me@example.com\n#site=http://example.com\nA=1");
        assertEquals("Me", p.getInfo("author"));
        assertEquals("Test", p.getInfo("name"));
        assertEquals("tt", p.getInfo("shortname"));
        assertEquals("me@example.com", p.getInfo("contact"));
        assertEquals("http://example.com", p.getInfo("site"));
        // info lines are not translation entries
        assertNull(p.getKey("#author"));
        assertEquals("1", p.getKey("A"));
    }

    @Test
    void missingInfoKeysAreEmpty() {
        LanguageProvider p = parse("#author=Me\n#name=");
        assertEquals("Me", p.getInfo("author"));
        assertEquals("", p.getInfo("name"));
        assertEquals("", p.getInfo("site"));
        assertEquals("", new LanguageProvider().getInfo("author"));
    }

    @Test
    void invalidInfoKeyIsIgnored() {
        LanguageProvider p = parse("#foo=bar\n#Author=X\nA=1");
        assertNull(p.getInfo("foo"));
        assertNull(p.getInfo("Author"));
        assertEquals("", p.getInfo("author"));
        assertNull(p.getKey("#foo"));
        assertNull(p.getKey("foo"));
        assertEquals("1", p.getKey("A"));
    }

    @Test
    void lineWithoutEqualsIsIgnored() {
        // The original threw on such a line; the port skips it.
        LanguageProvider p = parse("A=1\njust some text\n#author\nB=2");
        assertEquals("1", p.getKey("A"));
        assertEquals("2", p.getKey("B"));
        assertNull(p.getKey("just some text"));
        assertEquals("", p.getInfo("author"));
    }

    @Test
    void duplicateKeyKeepsFirstValue() {
        // The original threw (Dictionary.Add); the port keeps the first value.
        LanguageProvider p = parse("A=first\nA=second");
        assertEquals("first", p.getKey("A"));
    }

    @Test
    void reopeningReplacesPreviousContent() {
        LanguageProvider p = parse("#author=Me\nA=1\nB=2");
        p.openFile("B=3");
        assertNull(p.getKey("A"));
        assertEquals("3", p.getKey("B"));
        assertEquals("", p.getInfo("author"));
        assertEquals("B=3", p.getLanguageFile());
    }

    @Test
    void nullContentIsEmpty() {
        LanguageProvider p = parse(null);
        assertEquals("", p.getLanguageFile());
        assertNull(p.getKey("A"));
    }

    @Test
    void languageUpdatedIsRaisedOnEveryOpen() {
        LanguageProvider p = new LanguageProvider();
        int[] count = {0};
        Runnable l = () -> count[0]++;
        p.addLanguageUpdatedListener(l);
        p.openFile("A=1");
        p.openFile("A=1");
        assertEquals(2, count[0]);
        p.removeLanguageUpdatedListener(l);
        p.openFile("A=2");
        assertEquals(2, count[0]);
    }

    @Test
    void listenerSeesNewContent() {
        LanguageProvider p = new LanguageProvider();
        String[] seen = new String[1];
        p.addLanguageUpdatedListener(() -> seen[0] = p.getKey("A"));
        p.openFile("A=new");
        assertEquals("new", seen[0]);
    }
}
