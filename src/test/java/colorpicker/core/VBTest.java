package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for the VB.NET numeric semantics helpers in {@link VB}. */
class VBTest {

    @ParameterizedTest(name = "Math.Round({0}) = {1}")
    @CsvSource({
            "0.5, 0",
            "1.5, 2",
            "2.5, 2",
            "3.5, 4",
            "-0.5, 0",
            "-1.5, -2",
            "-2.5, -2",
            "2.4999, 2",
            "2.5001, 3",
            "127.5, 128",
            "178.5, 178",
    })
    void roundIsHalfToEven(double x, int expected) {
        // delta 0 so that -0.0 equals 0
        assertEquals(expected, VB.round(x), 0.0);
        assertEquals(expected, VB.cint(x));
    }

    @Test
    void hexIsUpperCaseUnpadded() {
        assertEquals("0", VB.hex(0));
        assertEquals("A", VB.hex(10));
        assertEquals("FF", VB.hex(255));
        assertEquals("FFFFFF", VB.hex(16777215));
        // Hex(-1) in VB.NET is the two's complement "FFFFFFFF"
        assertEquals("FFFFFFFF", VB.hex(-1));
    }

    @Test
    void hex2IsPaddedToTwoDigits() {
        assertEquals("00", VB.hex2(0));
        assertEquals("05", VB.hex2(5));
        assertEquals("0F", VB.hex2(15));
        assertEquals("10", VB.hex2(16));
        assertEquals("FF", VB.hex2(255));
    }

    @Test
    void nextInclusiveCoversBothBounds() {
        Random random = new Random(42);
        boolean[] seen = new boolean[256];
        for (int i = 0; i < 20000; i++) {
            int v = VB.nextInclusive(random, 0, 255);
            assertTrue(v >= 0 && v <= 255, "out of range: " + v);
            seen[v] = true;
        }
        for (int v = 0; v < 256; v++) {
            assertTrue(seen[v], "never produced " + v);
        }
    }

    @Test
    void nextInclusiveWithOffsetRange() {
        Random random = new Random(1);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 5000; i++) {
            int v = VB.nextInclusive(random, 50, 100);
            assertTrue(v >= 50 && v <= 100, "out of range: " + v);
            sawMin |= v == 50;
            sawMax |= v == 100;
        }
        assertTrue(sawMin && sawMax);
    }

    @Test
    void nextInclusiveDegenerateRange() {
        Random random = new Random(0);
        assertEquals(7, VB.nextInclusive(random, 7, 7));
        assertEquals(7, VB.nextInclusive(random, 7, 3));
    }
}
