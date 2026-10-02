package colorpicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SingleInstance}. Within one JVM a lock held by another
 * channel counts as held by another process, which is what the claims see here.
 */
class SingleInstanceTest {

    @TempDir
    Path dir;

    private Path file(String ext) {
        return dir.resolve("colorpicker-" + System.getProperty("user.name", "user") + ext);
    }

    @Test
    void secondLaunchActivatesTheOwner() throws Exception {
        CountDownLatch activated = new CountDownLatch(1);
        assertTrue(SingleInstance.claim(dir, activated::countDown));
        assertTrue(Files.exists(file(".lock")));
        assertTrue(Files.exists(file(".sock")));

        assertFalse(SingleInstance.claim(dir, () -> {
            throw new AssertionError("the second launch must not become the owner");
        }));
        assertTrue(activated.await(5, TimeUnit.SECONDS));
    }

    @Test
    void staleSocketIsReplacedByTheOwner() throws Exception {
        Files.createFile(file(".sock"));
        CountDownLatch activated = new CountDownLatch(1);
        assertTrue(SingleInstance.claim(dir, activated::countDown));
        assertFalse(SingleInstance.claim(dir, () -> { }));
        assertTrue(activated.await(5, TimeUnit.SECONDS));
    }

    @Test
    void launchDuringOwnerStartupWaitsForItsSocket() throws Exception {
        try (FileChannel ch = FileChannel.open(file(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = ch.lock()) {
            assertTrue(lock.isValid());
            CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> SingleInstance.claim(dir, () -> { }));
            Thread.sleep(400);
            assertFalse(second.isDone(), "must wait for the owner instead of starting");
            try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
                server.bind(UnixDomainSocketAddress.of(file(".sock")));
                try (SocketChannel client = server.accept()) {
                    ByteBuffer buf = ByteBuffer.allocate(64);
                    client.read(buf);
                    buf.flip();
                    assertEquals("activate\n", StandardCharsets.US_ASCII.decode(buf).toString());
                }
            }
            assertFalse(second.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void launchTakesOverWhenTheOwnerExits() throws Exception {
        FileChannel ch = FileChannel.open(file(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock = ch.lock();
        assertTrue(lock.isValid());
        CompletableFuture<Boolean> second = CompletableFuture.supplyAsync(() -> SingleInstance.claim(dir, () -> { }));
        Thread.sleep(300);
        ch.close();
        assertTrue(second.get(5, TimeUnit.SECONDS));
        assertTrue(Files.exists(file(".sock")));
    }
}
