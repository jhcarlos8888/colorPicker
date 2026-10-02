package colorpicker;

import colorpicker.settings.AppSettings;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Single-instance support (the original was a VB "single instance
 * application"): a second launch asks the running instance to show its window
 * and exits.
 *
 * <p>Ownership is claimed at start-up with an exclusive lock on
 * {@code colorpicker-<user>.lock}, held for the whole life of the process (the
 * kernel releases it on exit or crash, so it never goes stale). The owner then
 * listens on the Unix domain socket {@code colorpicker-<user>.sock}; both files
 * live in {@code $XDG_RUNTIME_DIR} (or the config directory). The lock file is
 * never deleted, since unlinking a lock file lets two processes lock
 * different files.
 */
final class SingleInstance {

    private static final String MESSAGE = "activate\n";
    /** How long a second launch waits for the owner's socket (it may still be binding). */
    private static final long WAIT_MILLIS = 3000;
    private static final long RETRY_MILLIS = 100;

    private SingleInstance() {
    }

    private static Path runtimeDirectory() {
        String runtime = System.getenv("XDG_RUNTIME_DIR");
        return runtime != null && !runtime.isBlank() && Files.isDirectory(Paths.get(runtime))
                ? Paths.get(runtime)
                : AppSettings.configDirectory();
    }

    private static String baseName() {
        return "colorpicker-" + System.getProperty("user.name", "user");
    }

    /**
     * Claims the single instance, see {@link #claim(Path, Runnable)}.
     */
    static boolean claim(Runnable onActivate) {
        return claim(runtimeDirectory(), onActivate);
    }

    /**
     * Claims the single instance in {@code dir}. If no other process owns it,
     * this process becomes the owner and {@code onActivate} runs (on a
     * background thread) each time a later launch connects. Otherwise the
     * owner is asked to come to the front.
     *
     * <p>Failures are not fatal: when the lock or the socket cannot be used the
     * application simply behaves as a multi-instance one.
     *
     * @return {@code true} if this process should go on starting, {@code false}
     *         if another instance owns the application (this process should exit)
     */
    static boolean claim(Path dir, Runnable onActivate) {
        Path sock = dir.resolve(baseName() + ".sock");
        Path lockPath = dir.resolve(baseName() + ".lock");
        FileChannel lockChannel;
        try {
            Files.createDirectories(dir);
            lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException | UnsupportedOperationException e) {
            System.err.println("ColorPicker: single-instance support disabled: " + e);
            return !sendActivate(sock);
        }
        FileLock lock;
        try {
            long deadline = System.nanoTime() + WAIT_MILLIS * 1_000_000L;
            while ((lock = tryLock(lockChannel)) == null) {
                // Another process owns it: it may still be binding its socket, or exiting.
                if (sendActivate(sock)) {
                    closeQuietly(lockChannel);
                    return false;
                }
                if (System.nanoTime() - deadline >= 0) {
                    System.err.println("ColorPicker: another instance is running but does not answer");
                    closeQuietly(lockChannel);
                    return false;
                }
                Thread.sleep(RETRY_MILLIS);
            }
        } catch (IOException e) {
            System.err.println("ColorPicker: single-instance support disabled: " + e);
            closeQuietly(lockChannel);
            return !sendActivate(sock);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeQuietly(lockChannel);
            return true;
        }
        if (!listen(sock, lock, onActivate)) {
            closeQuietly(lockChannel);
        }
        return true;
    }

    /** @return the lock, or {@code null} if another owner holds it */
    private static FileLock tryLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    /**
     * Tells the running instance to come to the front.
     *
     * @return {@code true} if it was reached
     */
    private static boolean sendActivate(Path sock) {
        if (!Files.exists(sock)) {
            return false;
        }
        try (SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            ch.connect(UnixDomainSocketAddress.of(sock));
            ch.write(ByteBuffer.wrap(MESSAGE.getBytes(StandardCharsets.US_ASCII)));
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * Binds the socket of the owner of {@code lock} and starts accepting later
     * launches.
     *
     * @return {@code false} if the socket could not be bound
     */
    private static boolean listen(Path sock, FileLock lock, Runnable onActivate) {
        ServerSocketChannel server;
        try {
            // Only the lock owner gets here, so any file left is a stale one.
            Files.deleteIfExists(sock);
            server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        } catch (IOException | UnsupportedOperationException e) {
            System.err.println("ColorPicker: single-instance support disabled: " + e);
            return false;
        }
        try {
            server.bind(UnixDomainSocketAddress.of(sock));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            System.err.println("ColorPicker: single-instance support disabled: " + e);
            closeQuietly(server);
            return false;
        }
        Thread t = new Thread(() -> {
            while (server.isOpen()) {
                try (SocketChannel client = server.accept()) {
                    client.read(ByteBuffer.allocate(64));
                    onActivate.run();
                } catch (IOException e) {
                    if (!server.isOpen()) {
                        return;
                    }
                } catch (RuntimeException e) {
                    System.err.println("ColorPicker: single-instance activation failed: " + e);
                }
            }
        }, "colorpicker-single-instance");
        t.setDaemon(true);
        t.start();
        // Referencing the lock here also keeps it (and its channel) alive until exit.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeQuietly(server);
            try {
                if (lock.isValid()) {
                    Files.deleteIfExists(sock);
                }
            } catch (IOException e) {
                // nothing to do on exit
            }
        }, "colorpicker-single-instance-exit"));
        return true;
    }

    private static void closeQuietly(Channel channel) {
        try {
            channel.close();
        } catch (IOException e) {
            // already closed or unusable
        }
    }
}
