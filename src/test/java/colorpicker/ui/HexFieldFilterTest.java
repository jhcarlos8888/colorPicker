package colorpicker.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The text filter of the main window's code boxes: {@code MaxLength},
 * upper-casing, and for the hex box the removal of {@code #} and white space
 * so that a pasted {@code #RRGGBB} fits.
 */
class HexFieldFilterTest {

    private static PlainDocument document(String text, int maxLength, boolean upperCase, boolean hexCode)
            throws BadLocationException {
        PlainDocument d = new PlainDocument();
        d.insertString(0, text, null);
        d.setDocumentFilter(new MainWindow.LimitFilter(maxLength, upperCase, hexCode));
        return d;
    }

    /** Select all, then paste. */
    private static String pasteOverAll(PlainDocument d, String text) throws BadLocationException {
        d.replace(0, d.getLength(), text, null);
        return d.getText(0, d.getLength());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "#FF0000|FF0000",
        "' #ff0000 '|FF0000",
        "#f00|F00",
        "ff0000|FF0000",
        "#ABCDEF12|ABCDEF",
        "' #00ff00 '|00FF00",
    })
    void pastedHexCodes(String pasted, String expected) throws BadLocationException {
        assertEquals(expected, pasteOverAll(document("123456", 6, true, true), pasted));
    }

    @Test
    void hashOrSpacesAloneKeepTheSelection() throws BadLocationException {
        assertEquals("123456", pasteOverAll(document("123456", 6, true, true), "#"));
        assertEquals("123456", pasteOverAll(document("123456", 6, true, true), "   "));
    }

    @Test
    void typedHashIsDropped() throws BadLocationException {
        PlainDocument d = document("", 6, true, true);
        for (char c : "#f00".toCharArray()) {
            d.insertString(d.getLength(), String.valueOf(c), null);
        }
        assertEquals("F00", d.getText(0, d.getLength()));
    }

    @Test
    void otherBoxesKeepTheirText() throws BadLocationException {
        assertEquals("#12", pasteOverAll(document("", 3, false, false), "#1234"));
        assertEquals("1", pasteOverAll(document("", 3, false, false), "1"));
    }
}
