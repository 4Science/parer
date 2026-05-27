package it.ivscience.parer.worker.ftp;

import it.ivscience.parer.worker.ftp.impl.FtpClientImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Large-file streaming tests for {@link FtpClientImpl}.
 *
 * Reads real files from {@code <project-root>/principale/} and uploads them
 * via {@link FtpClientImpl} to a {@link NullFtpServer} (discards bytes, no
 * server-side heap pressure). Verifies:
 * <ol>
 *   <li>Every byte arrives at the server (no truncation).</li>
 *   <li>Heap increase stays well below file size (confirms streaming).</li>
 * </ol>
 *
 * <strong>To simulate K8s pod memory limits, run with a capped heap:</strong>
 * <pre>
 *   mvn test -Dtest=FtpClientImplLargeFileIT \
 *             -DargLine="-Xmx256m -Xms64m"
 * </pre>
 * The 5 GB file should transfer successfully even with {@code -Xmx256m} because
 * Apache Commons Net's {@code FTPClient.storeFile()} uses a fixed 4 KB buffer.
 */
class FtpClientImplLargeFileIT {

    /**
     * Max accepted heap increase during any single upload.
     * Even with GC noise, 64 MB is generous for a 4 KB I/O buffer.
     */
    private static final long MAX_HEAP_INCREASE_BYTES = 64L * 1024 * 1024;

    private static final String INPUT_FOLDER = "/input";
    private static final Path   PRINCIPALE   = Paths.get(System.getProperty("user.dir"), "principale");

    private NullFtpServer server;
    private FtpClientImpl client;

    // ─── JUnit lifecycle ──────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws IOException {
        server = new NullFtpServer();

        client = new FtpClientImpl();
        client.setHost("localhost");
        client.setPort(server.getPort());
        client.setUsername("parer");
        client.setPassword("secret");
        client.setInputFolder(INPUT_FOLDER);
        client.setDataTimeoutMs(600_000); // 10 min for multi-GB files
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) server.close();
    }

    // ─── Test data ────────────────────────────────────────────────────────────

    /**
     * Returns all regular files found in {@code principale/}.
     * Each entry is the file {@link Path}; the test name shows the filename + size.
     */
    static Stream<Path> principaleFiles() throws IOException {
        return Files.list(PRINCIPALE).filter(Files::isRegularFile).sorted();
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    /**
     * Uploads each file from {@code principale/} and asserts:
     * <ul>
     *   <li>All bytes arrive at the server.</li>
     *   <li>Heap increase stays below {@link #MAX_HEAP_INCREASE_BYTES}.</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("principaleFiles")
    void upload_allBytesArrive_heapBounded(Path file) throws Exception {
        long fileSize = Files.size(file);
        String objectCode = "OBJ-" + file.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
        String remoteFile = objectCode + "_" + file.getFileName();

        System.out.printf("[FtpIT] uploading %s (%.1f MB)%n",
                file.getFileName(), fileSize / 1024.0 / 1024.0);

        // ── Heap baseline ─────────────────────────────────────────────────
        System.gc();
        Thread.sleep(200);
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        AtomicLong peakHeap = new AtomicLong(mem.getHeapMemoryUsage().getUsed());
        long baselineHeap   = peakHeap.get();

        AtomicBoolean monitoring = new AtomicBoolean(true);
        Thread monitor = new Thread(() -> {
            while (monitoring.get()) {
                peakHeap.accumulateAndGet(mem.getHeapMemoryUsage().getUsed(), Math::max);
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }, "heap-monitor");
        monitor.setDaemon(true);
        monitor.start();

        // ── Upload ────────────────────────────────────────────────────────
        try (InputStream is = new FileInputStream(file.toFile())) {
            client.upload(objectCode, remoteFile, is, fileSize);
        } finally {
            monitoring.set(false);
            monitor.join(2_000);
        }

        // ── Assertions ────────────────────────────────────────────────────
        assertEquals(fileSize, server.getTotalBytesReceived(),
                "All bytes of " + file.getFileName() + " must arrive at the FTP server");

        long heapIncrease = peakHeap.get() - baselineHeap;
        System.out.printf("[FtpIT] heap increase: %.1f MB (file was %.1f MB)%n",
                heapIncrease / 1024.0 / 1024.0, fileSize / 1024.0 / 1024.0);

        assertTrue(heapIncrease < MAX_HEAP_INCREASE_BYTES,
                String.format(
                        "Heap increase during upload of %s must be < 64 MB but was %.1f MB " +
                        "(client is buffering — streaming is broken)",
                        file.getFileName(), heapIncrease / 1024.0 / 1024.0));
    }

    // =========================================================================
    // Null FTP Server — discards uploaded bytes, no server-side buffering
    // =========================================================================

    /**
     * Minimal FTP server (passive mode) that accepts connections, handles the
     * exact command sequence issued by {@link FtpClientImpl}, and discards all
     * uploaded data by counting bytes without accumulating them.
     *
     * Uses raw OutputStream writes (no PrintWriter) to guarantee that responses
     * are actually written — PrintWriter silently swallows IOExceptions by setting
     * an internal trouble flag, which would leave the client waiting for a reply
     * that never arrives.
     */
    static class NullFtpServer implements AutoCloseable {

        private final ServerSocket controlSocket;
        private final AtomicLong   totalBytesReceived = new AtomicLong(0);

        NullFtpServer() throws IOException {
            controlSocket = new ServerSocket(0);
            Thread t = new Thread(this::acceptLoop, "null-ftp-accept");
            t.setDaemon(true);
            t.start();
        }

        int  getPort()               { return controlSocket.getLocalPort(); }
        long getTotalBytesReceived() { return totalBytesReceived.get(); }

        private void acceptLoop() {
            while (!controlSocket.isClosed()) {
                try {
                    Socket ctrl = controlSocket.accept();
                    Thread t = new Thread(() -> {
                        try {
                            handleControl(ctrl);
                        } catch (IOException e) {
                            if (!controlSocket.isClosed()) {
                                System.err.println("[NullFtpServer] handleControl error: " + e);
                            }
                        }
                    }, "null-ftp-ctrl");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (!controlSocket.isClosed()) {
                        System.err.println("[NullFtpServer] accept error: " + e);
                    }
                }
            }
        }

        /**
         * Handles one FTP control connection using raw I/O.
         * Supports: USER, PASS, TYPE, PASV, CWD, MKD, STOR, QUIT.
         */
        private void handleControl(Socket ctrl) throws IOException {
            try (ctrl) {
                OutputStream rawOut = ctrl.getOutputStream();
                InputStream  rawIn  = ctrl.getInputStream();

                ftpWrite(rawOut, "220 NullFTP ready");

                ServerSocket dataSrv = null;
                String line;
                while ((line = ftpReadLine(rawIn)) != null) {
                    String cmd = line.trim().toUpperCase(Locale.ROOT);

                    if (cmd.startsWith("USER")) {
                        ftpWrite(rawOut, "331 Password required");

                    } else if (cmd.startsWith("PASS")) {
                        ftpWrite(rawOut, "230 Logged in");

                    } else if (cmd.startsWith("TYPE")) {
                        ftpWrite(rawOut, "200 Type set");

                    } else if (cmd.equals("PASV")) {
                        if (dataSrv != null) {
                            try { dataSrv.close(); } catch (IOException ignored) {}
                        }
                        dataSrv = new ServerSocket(0);
                        int p = dataSrv.getLocalPort();
                        ftpWrite(rawOut, "227 Entering Passive Mode (127,0,0,1,"
                                + (p >> 8) + "," + (p & 0xFF) + ")");

                    } else if (cmd.startsWith("CWD") || cmd.startsWith("MKD")) {
                        ftpWrite(rawOut, "250 OK");

                    } else if (cmd.startsWith("STOR")) {
                        if (dataSrv == null) {
                            ftpWrite(rawOut, "425 Use PASV first");
                            continue;
                        }
                        ftpWrite(rawOut, "150 Opening binary data connection");
                        ServerSocket ds = dataSrv;
                        dataSrv = null;
                        ds.setSoTimeout(30_000); // 30 s to accept the data connection
                        try (Socket data = ds.accept();
                             InputStream dataIn = data.getInputStream()) {
                            ds.close();
                            byte[] buf = new byte[65536];
                            int n;
                            while ((n = dataIn.read(buf)) != -1) {
                                totalBytesReceived.addAndGet(n);
                            }
                        } finally {
                            try { ds.close(); } catch (IOException ignored) {}
                        }
                        ftpWrite(rawOut, "226 Transfer complete");

                    } else if (cmd.startsWith("QUIT")) {
                        ftpWrite(rawOut, "221 Goodbye");
                        break;

                    } else {
                        ftpWrite(rawOut, "502 Not implemented");
                    }
                }

                if (dataSrv != null) {
                    try { dataSrv.close(); } catch (IOException ignored) {}
                }
            }
        }

        /** Writes one FTP response line with CRLF and flushes immediately. */
        private static void ftpWrite(OutputStream out, String msg) throws IOException {
            out.write((msg + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

        /**
         * Reads one line from the FTP control stream, stripping CR.
         * Returns {@code null} on EOF with no data.
         */
        private static String ftpReadLine(InputStream in) throws IOException {
            StringBuilder sb = new StringBuilder(128);
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') return sb.toString();
                if (c != '\r') sb.append((char) c);
            }
            return sb.length() > 0 ? sb.toString() : null;
        }

        @Override
        public void close() throws IOException {
            controlSocket.close();
        }
    }
}