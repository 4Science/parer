package it.ivscience.parer.worker.ftp;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.ftp.impl.FtpsClientImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.net.ssl.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FtpsClientImplTest {

    private static final String USERNAME     = "parer";
    private static final String PASSWORD     = "secret";
    private static final String INPUT_FOLDER = "/home/parer/input";

    /** Trust-all TrustManager used by test client to accept the self-signed server cert. */
    private static final TrustManager TRUST_ALL = new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}
        public void checkServerTrusted(X509Certificate[] c, String a) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    private NullFtpsServer server;
    private FtpsClientImpl client;

    @BeforeEach
    void setUp() throws Exception {
        server = new NullFtpsServer(USERNAME, PASSWORD);
        client = new FtpsClientImpl();
        client.setHost("localhost");
        client.setPort(server.getPort());
        client.setUsername(USERNAME);
        client.setPassword(PASSWORD);
        client.setInputFolder(INPUT_FOLDER);
        client.setTrustManager(TRUST_ALL);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) server.close();
    }

    // -------------------------------------------------------------------------

    @Test
    void uploadCreatesFileInCorrectDirectory() {
        byte[] content = "PDF file content".getBytes(StandardCharsets.UTF_8);

        client.upload("SIP-001", "SIP-001_document.pdf",
                new ByteArrayInputStream(content), content.length);

        byte[] received = server.getFile(INPUT_FOLDER + "/SIP-001/SIP-001_document.pdf");
        assertNotNull(received, "File must be present on the FTPS server");
        assertArrayEquals(content, received, "Uploaded content must match");
    }

    @Test
    void uploadWorksWhenObjectDirAlreadyExists() {
        byte[] content = "second upload".getBytes(StandardCharsets.UTF_8);

        client.upload("SIP-002", "SIP-002_first.tif",
                new ByteArrayInputStream(content), content.length);

        assertDoesNotThrow(() ->
                client.upload("SIP-002", "SIP-002_file.tif",
                        new ByteArrayInputStream(content), content.length));

        assertNotNull(server.getFile(INPUT_FOLDER + "/SIP-002/SIP-002_file.tif"));
    }

    @Test
    void uploadThrowsFunctionalExceptionOnWrongCredentials() {
        client.setPassword("wrong");

        assertThrows(FunctionalException.class, () ->
                client.upload("SIP-003", "SIP-003_file.pdf",
                        new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test
    void uploadThrowsTransientExceptionWhenServerUnreachable() {
        client.setPort(19999);
        client.setConnectTimeoutMs(500);

        assertThrows(TransientException.class, () ->
                client.upload("SIP-004", "SIP-004_file.pdf",
                        new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test
    void uploadStreamIsFullyTransferred() {
        byte[] content = new byte[100 * 1024];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 127);

        client.upload("SIP-005", "SIP-005_large.bin",
                new ByteArrayInputStream(content), content.length);

        byte[] received = server.getFile(INPUT_FOLDER + "/SIP-005/SIP-005_large.bin");
        assertNotNull(received);
        assertEquals(content.length, received.length, "Uploaded file size must match");
        assertArrayEquals(content, received, "Every byte of the stream must arrive intact");
    }

    // =========================================================================
    // Null FTPS Server — explicit TLS, no filesystem, stores uploaded bytes
    // =========================================================================

    /**
     * Minimal FTPS server (explicit TLS, passive mode) for unit testing.
     * Accepts one FTP control connection at a time, upgrades to TLS via AUTH TLS,
     * validates credentials, and stores uploaded file content in memory.
     *
     * The SSL context is backed by a pre-generated self-signed PKCS12 keystore
     * (CN=localhost, RSA-2048, validity 10 years) embedded as a Base64 constant.
     * No keytool or external dependency is required.
     */
    static class NullFtpsServer implements AutoCloseable {

        /*
         * Self-signed PKCS12 keystore: CN=localhost, RSA-2048, SHA384withRSA,
         * validity 3650 days (generated 2026-05-15, expires ~2036-05-12).
         * Password: "changeit"
         */
        private static final String KS_B64 =
            "MIIKMAIBAzCCCdoGCSqGSIb3DQEHAaCCCcsEggnHMIIJwzCCBaoGCSqGSIb3DQEHAaCCBZsE" +
            "ggWXMIIFkzCCBY8GCyqGSIb3DQEMCgECoIIFQDCCBTwwZgYJKoZIhvcNAQUNMFkwOAYJKoZI" +
            "hvcNAQUMMCsEFNa4ePgcPT6B9JZa5WqOteXJymQIAgInEAIBIDAMBggqhkiG9w0CCQUAMB0G" +
            "CWCGSAFlAwQBKgQQlj0GPCnpzwIS3+CGX0cIwwSCBNDB5edmF+TAJNJ0sdjhOKp7Hf3Ap7i8" +
            "te1KZgU+ORwNpoD3abk25uNbdG81ls2QPhSkooSLUDqoPeli3iyfymQ35RYc+26pyr1zFlPY9" +
            "+lj8nFkjO6gxqKUgoxHsLVHMoCe+qHLI8sLVVQzBqAvgPVs7d4p6oMpARioWMcTRyr0H9bZk" +
            "YMInYgDK3qXAbHVi00tRfXp6+OWtZTjyVBznINbXYf+WBb5tARj8jmssTX8HlO3k/LsCrK1D" +
            "9CHWrXNDQHDH0IrPWT2Kyzhac6LFLOtI+LFbxTNdV1Fzv6ZuEWyFi71/PUc61P+Gy7f9dVOA" +
            "TapJhdO+Q1nvLtAz9m57SLuezgaRjCwEKEM7Hb4Q+ta//E0yLRzQBe5adO9XgN6ZFlkXV5jH" +
            "tgqU6ii5RgONyPVdgL2y4A5TzPfLUcnoPmlhBDpHgsV8NMV+XqRl3kgpNCV5hKMSCKCIjY59" +
            "OB2fAe77WOmWwtt0nc3DzgmhBk7gIDVhHbJXcbevFH9I1tyBUIIZl56T76ToN0XGC3uo/Izn4" +
            "zV7fD6TzzVCQULiuISCuXlj2Me1SSMOiFvRoUp21Gpr8tc6tjLLUUZ6mGHjfPOFXwnsLP19sm" +
            "a0bEjHJMk2XyeUFCsPltw5vSK5luSpVsi+ZNCbvoo8Eqyc0GdoOnFDhpFUCzZUBE82xfGSHd" +
            "P/lEsicIAiduv0Tlsj9MhIhD12Yjqq0kPt7MPMEuhg40AvTFICenriBlRgUwfFUWq6nqy7yR" +
            "qUFafP/yf6nEyJ9+vCOT0Eb8pu1hQY/CgnhuFg6sJTHi2ZIl1pUbKAUhMNTNWWEqLTAQ/wl9" +
            "eoXzs3bkcvgHvMEriP1qQR/TzoXQyfOlXM+X64ThDEBPY9+wAaC0aoIWco2kQYUZBIUwi/oY8" +
            "kq3/nDooc5bYbWc/FOG9O+nt2rMBtHPlw64pySyqACOmWUCmIhL6tcnzdbZmoohT0kqwpIvsu" +
            "eQLOJfXv4kqIUGmkLG5XMGr6FsYDposjr3fSvQxNkIXIlyCq1CyuAgHieNu82NU7giSJ//oLa" +
            "98UJohKxZKixDIDmRiAIOOTWhgaz0aTdwmIggP8bt2hOLKQF1nc08ureyp14/6/kUGmYGNnXWZ" +
            "65ooaPTrYA3JQQH5erkknkeuyGdYX0LFlxgrx/9rcs3ovSH6tAYviKp0zgfi3d8RopiO5+6yBr" +
            "ko/NmEBspooXGcBnK9PMFjYCR5OP6lJ48QowxzecQlEjCvJLimo+MdICxEc2NBGobIQDPWoTQb" +
            "y2brXEyMaaWWUFLAOeMApXzpvkzRSydWKNHQK+lOx/6Q4/E90gcrC9SSlj1OQ316AkLb2V5Fj" +
            "wz3KBJvVCTCkcf5chjw1m/4vUUFpWVZJ7BdUMDeS3kkMD5dlygeZprXH10P+8V9sux+OZy0qi" +
            "qK1NHrAr02Y5SrX882RusSjDuvh9GWPxwCNmNhCJQ77htR3X0jimKZ3HLVy5MPrQjocg39ap0/" +
            "EMkX3XjvwMkr+qAqLPimCPrIYCOk37/Rfaaq0XmR0Wg4FhXKXNxMrCyiKD2iEFxrb6kt0QxAe" +
            "MruQQATqQ4MNM2qg0rsmDIyDpyRSoYSl41qaI51MUyyrqETE4RFcUvFNXOtuARJA0Axv56BKgI" +
            "y1kbU/gzO7TE8MBcGCSqGSIb3DQEJFDEKHggAdABlAHMAdDAhBgkqhkiG9w0BCRUxFAQSVGlt" +
            "ZSAxNzc4ODQ5NDQwNjc5MIIEEQYJKoZIhvcNAQcGoIIEAjCCA/4CAQAwggP3BgkqhkiG9w0BB" +
            "wEwZgYJKoZIhvcNAQUNMFkwOAYJKoZIhvcNAQUMMCsEFEd2E8R9Y9PGP/TFdn17MLteHFOdAg" +
            "InEAIBIDAMBggqhkiG9w0CCQUAMB0GCWCGSAFlAwQBKgQQM9PsuavLNPCsVN+aaptK8oCCA4D" +
            "re3LDKg9Wdczd0VN3vBiX+YCLPh2kuCwa49x1Fh2zAEq3dMqosuqdakyt/HZZLWzbA/isJjUS" +
            "62T2nPfDuW3YLRlh4cEtQyQYPjUqyNgQLxXCzIDKX3S41RcydpsFI91syTqTBqVmOEVo4LUky" +
            "DlNltrdQShC7qGCT6Jh+/kFL3+TzQWqr+fy0QkY8ICsXc5vsO3xs+U0GvsydBnzPweeM1IdRy" +
            "iYKPD7ko80URlc6/RxIGvkB8vVT33/n6PXhMKGNgowy1s46kJVGoXlrSVU3kHSap/rqyxf25/" +
            "jUU+FnrJTCn9io771odAwLyLntTOR7u3LlsljZs7f6RyIuF5DoDzE1QMMAJG+NtT6DTuiZRcv" +
            "lytAIzql4I2CW9KhwvID3AjIPjhACuHzRSSbe1+gSQGci6wBoc/6p/YBbbHkwe5mTopOh7svTV" +
            "mQBq5kDvxkL3uoB3sj02vOnKDKnc2HyTmJV4YFj6RsOyTkDJ3yCyXbmPEl74E5N2njb/cyZOq" +
            "Xr1kYio2heiEeDI85R0XcHV5QPKX76ZOzpWSA9RgIODrohroSvdH5owzf62/0LVYDEV1SS1Hb2" +
            "JX7khll1drt24qozzpU//nYhhNv3fdmda45vBqoWQRxnUL6TUHPiJpT6w6q4Evo0lEhZIdiWm/" +
            "FZ9YIVC0dZyuxwuAcpvHwr6wlLsevdhe9vn9XRvVByh51cxexbvni/QNoskyXwQvyRsYDZMtC3" +
            "CTOVJJxsRxta/PHa6yBmNul4LiW8q+Jp2tJ6xXkie1oxOcTcNz21+pn++87QfZSIhMNWz+8oQx" +
            "YlpPZVUV3UM/RkoXLcbJfJtFUeDok7vy8/vViK58ZQr1K6lw0a4kzvFZ8ajG9qQdCzY8Y3bdy4" +
            "VAyoCyUpeRQb3lwJNhd01o0KnN11LYg0UKWGP1Yv2bmUa3RnxygEOXtOdaJB/S9Xemwbvb2ZlQ" +
            "LDkZl9kENj71Py0PZz440NJz9wKjf6FjZc8tCPF4qtTOAV+unH0okp8j/Izlrz7sz37tYboJDs" +
            "j+DawiaO/CIyw490//gscCFphN7jCl/EeLGFBQOh+6hOlXHJLQ6+dhhrIzwqpDgc/W600Aii4s+" +
            "CuM55leqFXPjvwILQG9ZyEJn5/nLl7Hq6nrjRZiUbHJlJpLn0oC5mh3kupSU1eosyTNI5lLGi6" +
            "fu+e3tchV3mPCGSTBNMDEwDQYJYIZIAWUDBAIBBQAEIFATtQNOO0/h6lgnixDfXFQ0q9cxkATM" +
            "G6UqvUL6vvxVBBROGofOeaWvzFVddqXGj9Qd83sl4wICJxA=";

        private static final char[] KS_PASS = "changeit".toCharArray();

        private final ServerSocket                     controlSocket;
        private final SSLContext                       sslContext;
        private final String                           expectedUser;
        private final String                           expectedPass;
        private final ConcurrentHashMap<String, byte[]> files = new ConcurrentHashMap<>();

        NullFtpsServer(String user, String pass) throws Exception {
            this.expectedUser = user;
            this.expectedPass = pass;

            byte[] ksBytes = Base64.getDecoder().decode(KS_B64.replaceAll("\\s+", ""));
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new ByteArrayInputStream(ksBytes), KS_PASS);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, KS_PASS);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            controlSocket = new ServerSocket(0);
            Thread t = new Thread(this::acceptLoop, "null-ftps-accept");
            t.setDaemon(true);
            t.start();
        }

        int    getPort()             { return controlSocket.getLocalPort(); }
        byte[] getFile(String path)  { return files.get(path); }

        private void acceptLoop() {
            while (!controlSocket.isClosed()) {
                try {
                    Socket ctrl = controlSocket.accept();
                    Thread t = new Thread(() -> {
                        try { handleControl(ctrl); }
                        catch (Exception e) {
                            if (!controlSocket.isClosed()) {
                                System.err.println("[NullFtpsServer] error: " + e);
                            }
                        }
                    }, "null-ftps-ctrl");
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    if (!controlSocket.isClosed()) {
                        System.err.println("[NullFtpsServer] accept error: " + e);
                    }
                }
            }
        }

        /**
         * Handles one FTPS control connection.
         * Explicit TLS: starts plain, upgrades on AUTH TLS, then all commands are SSL.
         */
        private void handleControl(Socket ctrl) throws Exception {
            try (ctrl) {
                OutputStream out = ctrl.getOutputStream();
                InputStream  in  = ctrl.getInputStream();

                ftpWrite(out, "220 NullFTPS ready");

                ServerSocket dataSrv = null;
                boolean      protP   = false;

                String line;
                while ((line = ftpReadLine(in)) != null) {
                    if (line.isBlank()) continue;
                    String upper = line.trim().toUpperCase(Locale.ROOT);
                    String arg   = line.contains(" ") ? line.substring(line.indexOf(' ') + 1).trim() : "";

                    if (upper.startsWith("AUTH TLS") || upper.startsWith("AUTH SSL")) {
                        ftpWrite(out, "234 AUTH TLS OK");
                        // Upgrade control channel to TLS (server mode)
                        SSLSocket sslCtrl = (SSLSocket) sslContext.getSocketFactory()
                                .createSocket(ctrl, ctrl.getInetAddress().getHostAddress(), ctrl.getPort(), false);
                        sslCtrl.setUseClientMode(false);
                        sslCtrl.startHandshake();
                        out = sslCtrl.getOutputStream();
                        in  = sslCtrl.getInputStream();

                    } else if (upper.startsWith("USER")) {
                        ftpWrite(out, "331 Password required");

                    } else if (upper.startsWith("PASS")) {
                        if (arg.equals(expectedPass)) {
                            ftpWrite(out, "230 Logged in");
                        } else {
                            ftpWrite(out, "530 Login incorrect");
                        }

                    } else if (upper.startsWith("PBSZ")) {
                        ftpWrite(out, "200 PBSZ=0");

                    } else if (upper.startsWith("PROT")) {
                        protP = "P".equalsIgnoreCase(arg);
                        ftpWrite(out, "200 PROT " + arg + " set");

                    } else if (upper.startsWith("TYPE")) {
                        ftpWrite(out, "200 Type set");

                    } else if (upper.equals("PASV")) {
                        if (dataSrv != null) { try { dataSrv.close(); } catch (IOException ignored) {} }
                        dataSrv = protP
                                ? (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(0)
                                : new ServerSocket(0);
                        int p = dataSrv.getLocalPort();
                        ftpWrite(out, "227 Entering Passive Mode (127,0,0,1,"
                                + (p >> 8) + "," + (p & 0xFF) + ")");

                    } else if (upper.startsWith("CWD") || upper.startsWith("MKD")) {
                        ftpWrite(out, "250 OK");

                    } else if (upper.startsWith("STOR")) {
                        if (dataSrv == null) { ftpWrite(out, "425 Use PASV first"); continue; }
                        ftpWrite(out, "150 Opening data connection");
                        ServerSocket ds = dataSrv;
                        dataSrv = null;
                        ds.setSoTimeout(10_000);
                        String storPath = arg;
                        try (Socket data = ds.accept();
                             InputStream dataIn = data.getInputStream()) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = dataIn.read(buf)) != -1) baos.write(buf, 0, n);
                            files.put(storPath, baos.toByteArray());
                        } finally {
                            try { ds.close(); } catch (IOException ignored) {}
                        }
                        ftpWrite(out, "226 Transfer complete");

                    } else if (upper.startsWith("QUIT")) {
                        ftpWrite(out, "221 Goodbye");
                        break;

                    } else {
                        ftpWrite(out, "502 Not implemented");
                    }
                }
                if (dataSrv != null) { try { dataSrv.close(); } catch (IOException ignored) {} }
            }
        }

        private static void ftpWrite(OutputStream out, String msg) throws IOException {
            out.write((msg + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.flush();
        }

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
        public void close() throws IOException { controlSocket.close(); }
    }
}