package colorpicker.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import colorpicker.core.ImageColorExtractor.Phase;
import colorpicker.core.ImageColorExtractor.Result;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link ImageColorExtractor} ({@code GetImageColorForm} worker and
 * {@code ImageChecker}) using small generated image files.
 */
class ImageColorExtractorTest {

    private static final BooleanSupplier NEVER = () -> false;

    @TempDir
    Path dir;

    private static BufferedImage rgbImage(int width, int... pixels) {
        int height = pixels.length / width;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        return img;
    }

    private Path write(BufferedImage img, String format, String name) throws IOException {
        Path file = dir.resolve(name);
        assertTrue(ImageIO.write(img, format, file.toFile()), "no writer for " + format);
        return file;
    }

    private static IndexColorModel palette(int... rgb) {
        byte[] r = new byte[rgb.length];
        byte[] g = new byte[rgb.length];
        byte[] b = new byte[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            r[i] = (byte) (rgb[i] >> 16);
            g[i] = (byte) (rgb[i] >> 8);
            b[i] = (byte) rgb[i];
        }
        return new IndexColorModel(8, rgb.length, r, g, b);
    }

    private static BufferedImage indexedImage(IndexColorModel cm, int width, int... indices) {
        int height = indices.length / width;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, cm);
        img.getRaster().setSamples(0, 0, width, height, 0, indices);
        return img;
    }

    @Test
    void distinctColorsInFirstOccurrenceOrder() throws IOException {
        Path file = write(rgbImage(3,
                0xFF0000, 0x00FF00, 0xFF0000,
                0x0000FF, 0x00FF00, 0xFFFFFF,
                0x123456, 0xFF0000, 0x000000), "png", "colors.png");
        assertTrue(ImageColorExtractor.canExtract(file));

        Result result = ImageColorExtractor.extract(file, NEVER, null);
        assertEquals(9, result.totalPixels());
        assertEquals(6, result.distinctCount());
        assertArrayEquals(new int[] {0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF, 0x123456, 0x000000}, result.colors());
    }

    @Test
    void totalPixelCountIsWidthTimesHeight() throws IOException {
        BufferedImage img = new BufferedImage(7, 5, BufferedImage.TYPE_INT_RGB);
        Result result = ImageColorExtractor.extract(write(img, "png", "black.png"), NEVER, null);
        assertEquals(35, result.totalPixels());
        assertArrayEquals(new int[] {0x000000}, result.colors());
    }

    @Test
    void alphaIsIgnored() throws IOException {
        BufferedImage img = new BufferedImage(5, 1, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 5, 1, new int[] {0x80FF0000, 0xFFFF0000, 0x00FF0000, 0x40123456, 0xFF123456}, 0, 5);
        Path file = write(img, "png", "alpha.png");

        Result result = ImageColorExtractor.extract(file, NEVER, null);
        assertEquals(5, result.totalPixels());
        assertArrayEquals(new int[] {0xFF0000, 0x123456}, result.colors());
        // the in-memory image gives the same answer
        assertArrayEquals(result.colors(), ImageColorExtractor.extract(img, NEVER, null).colors());
    }

    @Test
    void grayscaleValuesAreTakenAsIs() throws IOException {
        // No linear-to-sRGB curve: grey 128 stays #808080 as in GDI+ / image editors.
        BufferedImage img = new BufferedImage(4, 1, BufferedImage.TYPE_BYTE_GRAY);
        img.getRaster().setSamples(0, 0, 4, 1, 0, new int[] {0, 128, 255, 128});
        Result result = ImageColorExtractor.extract(write(img, "png", "gray.png"), NEVER, null);
        assertArrayEquals(new int[] {0x000000, 0x808080, 0xFFFFFF}, result.colors());
        assertArrayEquals(new int[] {0x000000, 0x808080, 0xFFFFFF}, ImageColorExtractor.extract(img, NEVER, null).colors());
    }

    @Test
    void palettedPngAndGif() throws IOException {
        IndexColorModel cm = palette(0x112233, 0xABCDEF, 0x00FF00, 0xFEDCBA);
        BufferedImage img = indexedImage(cm, 3, 3, 1, 3, 0, 1, 3);
        int[] expected = {0xFEDCBA, 0xABCDEF, 0x112233};
        assertArrayEquals(expected, ImageColorExtractor.extract(write(img, "png", "indexed.png"), NEVER, null).colors());
        Path gif = write(img, "gif", "single.gif");
        assertTrue(ImageColorExtractor.canExtract(gif));
        assertFalse(ImageColorExtractor.isAnimated(gif));
        Result result = ImageColorExtractor.extract(gif, NEVER, null);
        assertEquals(6, result.totalPixels());
        assertArrayEquals(expected, result.colors());
    }

    @Test
    void bmpIsSupported() throws IOException {
        Path file = write(rgbImage(2, 0x010203, 0x040506, 0x040506, 0x010203), "bmp", "small.bmp");
        assertTrue(ImageColorExtractor.canExtract(file));
        assertArrayEquals(new int[] {0x010203, 0x040506}, ImageColorExtractor.extract(file, NEVER, null).colors());
    }

    @Test
    void jpegIsAcceptedAsSingleFrame() throws IOException {
        Path file = write(rgbImage(8, new int[64]), "jpg", "black.jpg");
        assertTrue(ImageColorExtractor.isValidImage(file));
        assertTrue(ImageColorExtractor.canExtract(file));
        assertEquals(64, ImageColorExtractor.extract(file, NEVER, null).totalPixels());
    }

    @Test
    void manyColorsKeepRowMajorOrder() {
        // 65536 distinct colours: more than the initial capacity of the result array
        int[] pixels = new int[256 * 256];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (i * 257) & 0xFFFFFF;
        }
        BufferedImage img = rgbImage(256, pixels);
        Result result = ImageColorExtractor.extract(img, NEVER, null);
        assertEquals(65536, result.totalPixels());
        assertEquals(65536, result.distinctCount());
        assertArrayEquals(pixels, result.colors());
    }

    @Test
    void animatedGifIsRejected() throws IOException {
        IndexColorModel cm = palette(0x000000, 0xFFFFFF);
        Path file = dir.resolve("animated.gif");
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(file.toFile())) {
            writer.setOutput(out);
            writer.prepareWriteSequence(null);
            writer.writeToSequence(new IIOImage(indexedImage(cm, 2, 0, 1, 1, 0), null, null), null);
            writer.writeToSequence(new IIOImage(indexedImage(cm, 2, 1, 0, 0, 1), null, null), null);
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        assertTrue(ImageColorExtractor.isValidImage(file));
        assertTrue(ImageColorExtractor.isAnimated(file));
        assertFalse(ImageColorExtractor.canExtract(file));
    }

    @Test
    void nonImagesAreRejected() throws IOException {
        Path text = dir.resolve("notes.png");
        Files.writeString(text, "this is not an image", StandardCharsets.UTF_8);
        Path empty = Files.createFile(dir.resolve("empty.gif"));
        Path missing = dir.resolve("missing.png");
        Path folder = Files.createDirectory(dir.resolve("folder.png"));

        for (Path p : List.of(text, empty, missing, folder)) {
            assertFalse(ImageColorExtractor.isValidImage(p), p.toString());
            assertFalse(ImageColorExtractor.isAnimated(p), p.toString());
            assertFalse(ImageColorExtractor.canExtract(p), p.toString());
        }
        assertFalse(ImageColorExtractor.canExtract(null));
        assertThrows(IOException.class, () -> ImageColorExtractor.extract(text, NEVER, null));
        assertThrows(IOException.class, () -> ImageColorExtractor.extract(empty, NEVER, null));
    }

    @Test
    void hugeImageIsReportedAsTooBig() throws IOException {
        // A PNG header claiming 100000 x 100000 pixels, no pixel data.
        Path file = dir.resolve("huge.png");
        Files.write(file, pngHeaderOnly(100_000, 100_000));
        assertThrows(ImageColorExtractor.ImageTooBigException.class,
                () -> ImageColorExtractor.extract(file, NEVER, null));
    }

    @Test
    void cancelledBeforeStart() throws IOException {
        Path file = write(rgbImage(2, 1, 2, 3, 4), "png", "c.png");
        assertThrows(CancellationException.class, () -> ImageColorExtractor.extract(file, () -> true, null));
        assertThrows(CancellationException.class,
                () -> ImageColorExtractor.extract(rgbImage(2, 1, 2, 3, 4), () -> true, null));
    }

    @Test
    void cancelledWhileGathering() {
        BufferedImage img = new BufferedImage(4, 10, BufferedImage.TYPE_INT_RGB);
        int[] polls = {0};
        List<Integer> gathering = new ArrayList<>();
        BooleanSupplier cancelAtFourthRow = () -> ++polls[0] > 3;
        assertThrows(CancellationException.class, () -> ImageColorExtractor.extract(img, cancelAtFourthRow,
                (phase, percent) -> gathering.add(percent)));
        assertEquals(4, polls[0]);
        assertEquals(List.of(0, 10, 20, 30), gathering);
    }

    @Test
    void progressIsReportedInOrderWithoutRepeats() throws IOException {
        BufferedImage img = new BufferedImage(3, 200, BufferedImage.TYPE_INT_RGB);
        Path file = write(img, "png", "tall.png");
        List<Phase> phases = new ArrayList<>();
        List<Integer> percents = new ArrayList<>();
        ImageColorExtractor.extract(file, NEVER, (phase, percent) -> {
            phases.add(phase);
            percents.add(percent);
        });
        assertEquals(Phase.INITIALIZING, phases.get(0));
        assertEquals(0, percents.get(0));
        int firstGathering = phases.indexOf(Phase.GATHERING);
        assertTrue(firstGathering > 0);
        assertEquals(0, percents.get(firstGathering));
        assertEquals(Phase.GATHERING, phases.get(phases.size() - 1));
        assertEquals(100, percents.get(percents.size() - 1));
        for (int i = 1; i < phases.size(); i++) {
            assertTrue(phases.get(i).compareTo(phases.get(i - 1)) >= 0, "phase went back");
            if (phases.get(i) == phases.get(i - 1)) {
                assertTrue(percents.get(i) > percents.get(i - 1), "progress not increasing at " + i);
            }
        }
    }

    @Test
    void supportedSuffixesIncludeCommonFormats() {
        List<String> suffixes = ImageColorExtractor.supportedSuffixes();
        assertTrue(suffixes.containsAll(List.of("png", "jpg", "gif", "bmp")), suffixes.toString());
    }

    private static byte[] pngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(ihdr);
        data.writeInt(width);
        data.writeInt(height);
        data.write(new byte[] {8, 2, 0, 0, 0});
        writeChunk(out, "IHDR", ihdr.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return bytes.toByteArray();
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
