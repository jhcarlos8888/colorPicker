package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link NumericInput}: the original accepted a text box value when
 * {@code Text <> "" And IsNumeric(Text) And isCompatibleNumeric(Text)} and
 * the value was in range.
 */
class NumericInputTest {

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1,0", "+1", "-1", ".5", ",5", "1-", "1+"})
    void incompatibleNumerics(String s) {
        assertFalse(NumericInput.isCompatibleNumeric(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "12", "255", "abc", " 12 "})
    void compatibleNumerics(String s) {
        assertTrue(NumericInput.isCompatibleNumeric(s));
    }

    @ParameterizedTest(name = "parseUnsigned(\"{0}\") = {1}")
    @CsvSource({
            "0, 0",
            "12, 12",
            "255, 255",
            "007, 7",
            "'  12  ', 12",
            "' 12 ', 12",
            "256, 256",
            "999999999999999999, 999999999999999999",
    })
    void parseUnsignedValid(String s, long expected) {
        assertEquals(expected, NumericInput.parseUnsigned(s));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "12a", "a12", "1.5", "1,0", "-1", "+1", "1 2", "0x10", "1e2", "½",
            // longer than a long / more than 18 digits
            "9223372036854775807", "99999999999999999999", "123456789012345678901234567890"})
    void parseUnsignedInvalid(String s) {
        assertNull(NumericInput.parseUnsigned(s));
    }

    @ParameterizedTest(name = "parseInRange(\"{0}\", 0, 255) = {1}")
    @CsvSource({
            "0, 0",
            "1, 1",
            "255, 255",
            "' 12 ', 12",
            "0100, 100",
    })
    void parseInRangeValid(String s, int expected) {
        assertEquals(expected, NumericInput.parseInRange(s, 0, 255));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"256", "1000", "12a", "1.5", "-1", "+1", "1,0", "99999999999999999999"})
    void parseInRangeInvalid(String s) {
        assertNull(NumericInput.parseInRange(s, 0, 255));
    }

    @Test
    void otherRanges() {
        // hue 0-360, saturation / value 0-100
        assertEquals(360, NumericInput.parseInRange("360", 0, 360));
        assertNull(NumericInput.parseInRange("361", 0, 360));
        assertEquals(100, NumericInput.parseInRange("100", 0, 100));
        assertNull(NumericInput.parseInRange("101", 0, 100));
        // decimal colour code 0-16777215
        assertEquals(16777215, NumericInput.parseInRange("16777215", 0, 16777215));
        assertNull(NumericInput.parseInRange("16777216", 0, 16777215));
        assertNull(NumericInput.parseInRange("4", 5, 10));
    }
}
