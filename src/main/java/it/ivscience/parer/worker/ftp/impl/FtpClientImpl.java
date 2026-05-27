package it.ivscience.parer.worker.ftp.impl;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.ftp.FtpClient;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * Real implementation of {@link FtpClient} via Apache Commons Net.
 *
 * Each invocation of {@link #upload} opens a dedicated FTP connection,
 * transfers the file in direct streaming (S3 InputStream → FTP data socket)
 * and disconnects. It never writes to the worker's local filesystem.
 *
 * Thread-safety: {@link FTPClient} is not thread-safe; since each upload uses
 * its own instance, the method is safe for the parallel pipeline.
 *
 * Directory structure on the server (§ 3.3.3.1):
 * <pre>
 *   &lt;inputFolder&gt;/
 *     └─ &lt;objectCode&gt;/
 *         ├─ &lt;objectCode&gt;_file1.pdf
 *         └─ &lt;objectCode&gt;_file2.tif
 * </pre>
 *
 * XML Wiring: ftp-services.xml, "!mock" profile.
 */
public class FtpClientImpl implements FtpClient {

    private static final Logger log = LogManager.getLogger(FtpClientImpl.class);

    // Injected via <property> in ftp-services.xml
    private String host;
    private int    port             = 21;
    private String username;
    private String password;
    private String inputFolder;
    private int    connectTimeoutMs = 15_000;
    private int    dataTimeoutMs    = 120_000;

    public void setHost(String host)                      { this.host = host; }
    public void setPort(int port)                         { this.port = port; }
    public void setUsername(String username)              { this.username = username; }
    public void setPassword(String password)              { this.password = password; }
    public void setInputFolder(String inputFolder)        { this.inputFolder = inputFolder; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public void setDataTimeoutMs(int dataTimeoutMs)       { this.dataTimeoutMs = dataTimeoutMs; }

    @Override
    public void upload(String objectCode, String remoteFileName, InputStream content, long contentLength) {
        FTPClient ftp = new FTPClient();
        try {
            // 1. Connection
            ftp.setConnectTimeout(connectTimeoutMs);
            ftp.setDataTimeout(dataTimeoutMs);
            ftp.connect(host, port);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                throw new FunctionalException(
                        "FTP connect refused by " + host + ":" + port + " — " + ftp.getReplyString().trim());
            }

            // 2. Login
            if (!ftp.login(username, password)) {
                throw new FunctionalException(
                        "FTP login failed for user '" + username + "' on " + host + " — " + ftp.getReplyString().trim());
            }

            // 3. Passive mode and binary transfer
            ftp.enterLocalPassiveMode();
            ftp.setFileType(FTP.BINARY_FILE_TYPE);

            // 4. Create (if necessary) the object directory
            String objectDir = inputFolder + "/" + objectCode;
            ensureDirectory(ftp, objectDir);

            // 5. Streaming upload: S3 InputStream → FTP data socket (no local buffer)
            log.debug("FTP upload started: {}:{}{}/{} ({} bytes)", host, port, objectDir, remoteFileName, contentLength);
            boolean stored = ftp.storeFile(objectDir + "/" + remoteFileName, content);
            if (!stored) {
                throw new TransientException(
                        "FTP storeFile failed for " + remoteFileName + " — " + ftp.getReplyString().trim());
            }

            log.info("FTP upload OK: object={} file={} size={} bytes", objectCode, remoteFileName, contentLength);

        } catch (IOException e) {
            throw new TransientException("FTP I/O error during upload of " + remoteFileName + " on " + host, e);
        } finally {
            if (ftp.isConnected()) {
                try { ftp.logout(); }     catch (IOException ignored) {}
                try { ftp.disconnect(); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * Creates the directory on the FTP server if it doesn't already exist.
     * Navigates the path component by component, creating missing parts.
     */
    private void ensureDirectory(FTPClient ftp, String dir) throws IOException {
        if (ftp.changeWorkingDirectory(dir)) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String part : dir.split("/")) {
            if (part.isEmpty()) continue;
            current.append("/").append(part);
            String path = current.toString();
            if (!ftp.changeWorkingDirectory(path)) {
                ftp.makeDirectory(path);
                if (!ftp.changeWorkingDirectory(path)) {
                    throw new TransientException("Unable to create/access FTP directory: " + path
                            + " — " + ftp.getReplyString().trim());
                }
            }
        }
    }
}