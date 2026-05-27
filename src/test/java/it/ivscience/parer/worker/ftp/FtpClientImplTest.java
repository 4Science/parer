package it.ivscience.parer.worker.ftp;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.ftp.impl.FtpClientImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FtpClientImplTest {

    private static final String USERNAME    = "parer";
    private static final String PASSWORD    = "secret";
    private static final String HOME_DIR    = "/home/parer";
    private static final String INPUT_FOLDER = "/home/parer/input";

    private FakeFtpServer server;
    private FileSystem     fileSystem;
    private FtpClientImpl  client;

    @BeforeEach
    void setUp() {
        fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry(HOME_DIR));
        fileSystem.add(new DirectoryEntry(INPUT_FOLDER));

        server = new FakeFtpServer();
        server.addUserAccount(new UserAccount(USERNAME, PASSWORD, HOME_DIR));
        server.setFileSystem(fileSystem);
        server.setServerControlPort(0); // porta casuale per evitare conflitti
        server.start();

        client = new FtpClientImpl();
        client.setHost("localhost");
        client.setPort(server.getServerControlPort());
        client.setUsername(USERNAME);
        client.setPassword(PASSWORD);
        client.setInputFolder(INPUT_FOLDER);
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // -------------------------------------------------------------------------

    @Test
    void uploadCreatesFileInCorrectDirectory() throws IOException {
        byte[] content = "PDF file content".getBytes(StandardCharsets.UTF_8);

        client.upload("SIP-001", "SIP-001_document.pdf",
                new ByteArrayInputStream(content), content.length);

        FileEntry uploaded = (FileEntry) fileSystem.getEntry(INPUT_FOLDER + "/SIP-001/SIP-001_document.pdf");
        assertNotNull(uploaded, "The file must be present on the FTP server");
        assertArrayEquals(content, readBytes(uploaded),
                "The uploaded content must match the provided stream");
    }

    @Test
    void uploadWorksWhenObjectDirAlreadyExists() {
        fileSystem.add(new DirectoryEntry(INPUT_FOLDER + "/SIP-002"));
        byte[] content = "second upload".getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() ->
                client.upload("SIP-002", "SIP-002_file.tif",
                        new ByteArrayInputStream(content), content.length));

        assertNotNull(fileSystem.getEntry(INPUT_FOLDER + "/SIP-002/SIP-002_file.tif"));
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
        client.setPort(19999); // port where no one is listening
        client.setConnectTimeoutMs(500);

        assertThrows(TransientException.class, () ->
                client.upload("SIP-004", "SIP-004_file.pdf",
                        new ByteArrayInputStream(new byte[0]), 0));
    }

    @Test
    void uploadStreamIsFullyTransferred() throws IOException {
        // 100 KB of random data to verify no truncation
        byte[] content = new byte[100 * 1024];
        for (int i = 0; i < content.length; i++) content[i] = (byte) (i % 127);

        client.upload("SIP-005", "SIP-005_large.bin",
                new ByteArrayInputStream(content), content.length);

        FileEntry uploaded = (FileEntry) fileSystem.getEntry(INPUT_FOLDER + "/SIP-005/SIP-005_large.bin");
        assertNotNull(uploaded);
        byte[] actual = readBytes(uploaded);
        assertEquals(content.length, actual.length, "The uploaded file size must match");
        assertArrayEquals(content, actual, "Every byte of the stream must arrive intact on the FTP server");
    }

    // -------------------------------------------------------------------------

    private static byte[] readBytes(FileEntry entry) throws IOException {
        try (InputStream is = entry.createInputStream()) {
            return is.readAllBytes();
        }
    }
}