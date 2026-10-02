package colorpicker.core;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DirectColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.stream.ImageInputStream;

/**
 * Reads an image file and lists its distinct colours (the work done by
 * {@code GetImageColorForm.BackgroundWorker1_DoWork}, plus the checks of
 * {@code ImageChecker.IsValidImage} / {@code IsAnimatedGif}).
 *
 * <p>Colours are returned as {@code 0xRRGGBB} ints in the order of their first
 * occurrence (row by row, left to right); alpha is ignored, exactly like the
 * original which dropped it before its second {@code Distinct()}. A 2^24-bit
 * set keeps the scan linear and allocation free.
 *
 * <p>No Swing here: the methods run on a worker thread, support cancellation
 * through a {@link BooleanSupplier} (a {@link CancellationException} is thrown)
 * and report progress through a {@link ProgressListener}.
 */
public final class ImageColorExtractor {

    /** Image formats offered in the file chooser, when an ImageIO reader exists. */
    private static final List<String> PREFERRED_SUFFIXES =
            List.of("png", "jpg", "jpeg", "gif", "bmp", "wbmp", "tif", "tiff");

    /** Number of 24-bit colours: the most distinct colours an image can have. */
    public static final int COLOR_SPACE_SIZE = 1 << 24;
    private static final int INITIAL_CAPACITY = 4096;

    /** Work phases, matching the statuses of the original tool. */
    public enum Phase {
        /** Decoding the file ({@code MSG_INITIALIZING}). */
        INITIALIZING,
        /** Scanning the pixels ({@code MSG_GATHERING}). */
        GATHERING
    }

    /** Receives the progress of a phase, 0 - 100, only when the value changes. */
    @FunctionalInterface
    public interface ProgressListener {
        void progress(Phase phase, int percent);
    }

    /** Thrown when an image cannot possibly fit in memory ({@code MSG_IMGTOOBIG}). */
    public static final class ImageTooBigException extends IOException {
        private static final long serialVersionUID = 1L;

        public ImageTooBigException(long width, long height) {
            super("Image is too big: " + width + " x " + height);
        }
    }

    /** Outcome of an extraction. */
    public static final class Result {
        private final long totalPixels;
        private final int[] colors;

        Result(long totalPixels, int[] colors) {
            this.totalPixels = totalPixels;
            this.colors = colors;
        }

        /** Number of pixels of the image ({@code {0}} of {@code MSG_DONE}). */
        public long totalPixels() {
            return totalPixels;
        }

        /** Number of distinct colours ({@code {1}} of {@code MSG_DONE}). */
        public int distinctCount() {
            return colors.length;
        }

        /**
         * The distinct colours as {@code 0xRRGGBB}, in first-occurrence order.
         * The array is shared, not copied: callers must not modify it.
         */
        public int[] colors() {
            return colors;
        }
    }

    private ImageColorExtractor() {
    }

    /**
     * Largest pixel count that {@link #extract(Path, BooleanSupplier, ProgressListener)}
     * accepts with the current heap; bigger images fail with {@link ImageTooBigException}.
     */
    public static long maxPixels() {
        return Math.min(Integer.MAX_VALUE - 8L, Runtime.getRuntime().maxMemory() / 4);
    }

    /**
     * Suffixes (lower case, without dot) of the usual image formats that the
     * installed ImageIO readers can decode.
     */
    public static List<String> supportedSuffixes() {
        List<String> readable = new ArrayList<>();
        for (String s : ImageIO.getReaderFileSuffixes()) {
            readable.add(s.toLowerCase(Locale.ROOT));
        }
        List<String> result = new ArrayList<>();
        for (String s : PREFERRED_SUFFIXES) {
            if (readable.contains(s)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * {@code IsValidImage(file) And FileExists(file) And Not IsAnimatedGif(file)}:
     * the file exists, an ImageIO reader understands it and it holds a single
     * frame (animated GIFs and multi-page TIFFs are rejected, as GDI+ reported
     * them with several frames).
     */
    public static boolean canExtract(Path file) {
        return isRegularFile(file) && inspect(file) == 1;
    }

    /** {@code ImageChecker.IsValidImage}: an ImageIO reader can decode the header. */
    public static boolean isValidImage(Path file) {
        return isRegularFile(file) && inspect(file) >= 1;
    }

    /** {@code ImageChecker.IsAnimatedGif}: a valid image with more than one frame. */
    public static boolean isAnimated(Path file) {
        return isRegularFile(file) && inspect(file) > 1;
    }

    private static boolean isRegularFile(Path file) {
        return file != null && Files.isRegularFile(file) && Files.isReadable(file);
    }

    /** @return the number of frames, or 0 when the file is not a readable image */
    private static int inspect(Path file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                return 0;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return 0;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, true);
                if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) {
                    return 0;
                }
                // A JPEG may carry extra MPF preview streams that ImageIO counts
                // as images; GDI+ always saw a single frame there.
                if (isJpeg(reader)) {
                    return 1;
                }
                return Math.max(reader.getNumImages(true), 1);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static boolean isJpeg(ImageReader reader) throws IOException {
        String format = reader.getFormatName();
        return format != null && format.toLowerCase(Locale.ROOT).contains("jp");
    }

    /**
     * Decodes {@code file} and extracts its distinct colours.
     *
     * @param cancelled polled regularly; when it returns {@code true} the work
     *                  stops with a {@link CancellationException}
     * @param progress  may be {@code null}
     * @throws ImageTooBigException when the image cannot fit in memory
     * @throws IOException          when the file is not a readable image
     * @throws OutOfMemoryError     when decoding runs out of memory
     */
    public static Result extract(Path file, BooleanSupplier cancelled, ProgressListener progress)
            throws IOException {
        Objects.requireNonNull(file);
        Objects.requireNonNull(cancelled);
        ProgressListener listener = progress != null ? progress : (phase, percent) -> { };
        listener.progress(Phase.INITIALIZING, 0);
        BufferedImage image = read(file, cancelled, listener);
        return extract(image, cancelled, listener);
    }

    /** Extracts the distinct colours of an already decoded image. */
    public static Result extract(BufferedImage image, BooleanSupplier cancelled, ProgressListener progress) {
        Objects.requireNonNull(image);
        Objects.requireNonNull(cancelled);
        ProgressListener listener = progress != null ? progress : (phase, percent) -> { };
        int width = image.getWidth();
        int height = image.getHeight();
        long total = (long) width * height;
        listener.progress(Phase.GATHERING, 0);

        RowReader rows = rowReader(image);
        int[] row = new int[width];
        long[] seen = new long[COLOR_SPACE_SIZE / Long.SIZE];
        int[] colors = new int[(int) Math.min(INITIAL_CAPACITY, Math.max(total, 1))];
        int count = 0;
        int lastPercent = 0;
        for (int y = 0; y < height; y++) {
            if (cancelled.getAsBoolean()) {
                throw new CancellationException();
            }
            rows.read(y, row);
            for (int x = 0; x < width; x++) {
                int c = row[x] & 0xFFFFFF;
                int word = c >>> 6;
                long bit = 1L << c;
                if ((seen[word] & bit) == 0) {
                    seen[word] |= bit;
                    if (count == colors.length) {
                        colors = Arrays.copyOf(colors, (int) Math.min((long) count * 2, COLOR_SPACE_SIZE));
                    }
                    colors[count++] = c;
                }
            }
            int percent = (int) ((y + 1) * 100L / height);
            if (percent != lastPercent) {
                lastPercent = percent;
                listener.progress(Phase.GATHERING, percent);
            }
        }
        return new Result(total, count == colors.length ? colors : Arrays.copyOf(colors, count));
    }

    private static BufferedImage read(Path file, BooleanSupplier cancelled, ProgressListener listener)
            throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                throw new IIOException("Cannot open " + file);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IIOException("Unsupported image format: " + file);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long width = reader.getWidth(0);
                long height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new IIOException("Invalid image size: " + width + " x " + height);
                }
                long pixels = width * height;
                if (pixels > maxPixels()) {
                    throw new ImageTooBigException(width, height);
                }
                reader.addIIOReadProgressListener(new ReadProgress(cancelled, listener));
                BufferedImage image = reader.read(0);
                if (cancelled.getAsBoolean()) {
                    throw new CancellationException();
                }
                if (image == null) {
                    throw new IIOException("Cannot decode " + file);
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    /** Fills a row with {@code 0xRRGGBB} values (upper byte ignored by the caller). */
    @FunctionalInterface
    private interface RowReader {
        void read(int y, int[] row);
    }

    /**
     * Picks a fast raster reader for the common layouts. Grey images are read
     * raw: {@code BufferedImage.getRGB} would apply a linear-to-sRGB gamma
     * curve that GDI+ and image editors do not.
     */
    private static RowReader rowReader(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        WritableRaster raster = image.getRaster();
        int width = image.getWidth();
        int bands = raster.getNumBands();

        if (cm instanceof IndexColorModel icm && bands == 1) {
            int size = Math.max(icm.getMapSize(), 1 << Math.min(icm.getPixelSize(), 16));
            int[] lut = new int[size];
            icm.getRGBs(lut);
            int[] samples = new int[width];
            return (y, row) -> {
                raster.getSamples(0, y, width, 1, 0, samples);
                for (int x = 0; x < width; x++) {
                    int s = samples[x];
                    row[x] = s >= 0 && s < lut.length ? lut[s] : 0;
                }
            };
        }

        if (cm instanceof ComponentColorModel && !cm.isAlphaPremultiplied()) {
            ColorSpace cs = cm.getColorSpace();
            if (cs.getType() == ColorSpace.TYPE_GRAY && cm.getNumColorComponents() == 1) {
                int bits = cm.getComponentSize(0);
                if (bits > 0 && bits <= 16 && raster.getTransferType() != DataBuffer.TYPE_FLOAT
                        && raster.getTransferType() != DataBuffer.TYPE_DOUBLE) {
                    int max = (1 << bits) - 1;
                    int[] samples = new int[width];
                    return (y, row) -> {
                        raster.getSamples(0, y, width, 1, 0, samples);
                        for (int x = 0; x < width; x++) {
                            int v = bits == 8 ? samples[x] : (samples[x] * 255 + max / 2) / max;
                            row[x] = v * 0x010101;
                        }
                    };
                }
            }
            if (cs.isCS_sRGB() && cm.getNumColorComponents() == 3 && bands >= 3
                    && raster.getTransferType() == DataBuffer.TYPE_BYTE
                    && cm.getComponentSize(0) == 8 && cm.getComponentSize(1) == 8 && cm.getComponentSize(2) == 8) {
                int[] pixels = new int[width * bands];
                return (y, row) -> {
                    raster.getPixels(0, y, width, 1, pixels);
                    for (int x = 0, i = 0; x < width; x++, i += bands) {
                        row[x] = (pixels[i] << 16) | (pixels[i + 1] << 8) | pixels[i + 2];
                    }
                };
            }
        }

        if (cm instanceof DirectColorModel dcm && !dcm.isAlphaPremultiplied()
                && raster.getTransferType() == DataBuffer.TYPE_INT
                && dcm.getRedMask() == 0xFF0000 && dcm.getGreenMask() == 0xFF00 && dcm.getBlueMask() == 0xFF) {
            return (y, row) -> raster.getDataElements(0, y, width, 1, row);
        }

        return (y, row) -> image.getRGB(0, y, width, 1, row, 0, width);
    }

    /** Reports decoding progress and aborts the reader on cancellation. */
    private static final class ReadProgress implements IIOReadProgressListener {
        private final BooleanSupplier cancelled;
        private final ProgressListener listener;
        private int lastPercent;

        ReadProgress(BooleanSupplier cancelled, ProgressListener listener) {
            this.cancelled = cancelled;
            this.listener = listener;
        }

        @Override
        public void imageProgress(ImageReader source, float percentageDone) {
            if (cancelled.getAsBoolean()) {
                source.abort();
                return;
            }
            int percent = Math.max(0, Math.min(100, (int) percentageDone));
            if (percent != lastPercent) {
                lastPercent = percent;
                listener.progress(Phase.INITIALIZING, percent);
            }
        }

        @Override
        public void sequenceStarted(ImageReader source, int minIndex) {
        }

        @Override
        public void sequenceComplete(ImageReader source) {
        }

        @Override
        public void imageStarted(ImageReader source, int imageIndex) {
        }

        @Override
        public void imageComplete(ImageReader source) {
        }

        @Override
        public void thumbnailStarted(ImageReader source, int imageIndex, int thumbnailIndex) {
        }

        @Override
        public void thumbnailProgress(ImageReader source, float percentageDone) {
        }

        @Override
        public void thumbnailComplete(ImageReader source) {
        }

        @Override
        public void readAborted(ImageReader source) {
        }
    }
}
