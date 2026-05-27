package it.ivscience.parer.worker.ftp.impl;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.ftp.FtpClient;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import javax.net.ssl.TrustManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;

/**
 * FTPS (explicit TLS, AUTH TLS) implementation of {@link FtpClient} via Apache Commons Net.
 *
 * Connects on port 21, upgrades to TLS via AUTH TLS, then protects the data
 * channel with PBSZ 0 / PROT P.  No local file buffering: the S3 InputStream
 * is streamed directly to the FTP data socket.
 *
 * Thread-safety: each upload creates its own {@link FTPSClient} instance.
 *
 * Wiring XML: ftp-services.xml, profile "ftps &amp; !mock".
 */
public class FtpsClientImpl implements FtpClient {

    private static final Logger log = LogManager.getLogger(FtpsClientImpl.class);

    private String       host;
    private int          port             = 21;
    private String       username;
    private String       password;
    private String       inputFolder;
    private int          connectTimeoutMs = 15_000;
    private int          dataTimeoutMs    = 120_000;
    private TrustManager trustManager     = null;

    public void setHost(String host)                      { this.host = host; }
    public void setPort(int port)                         { this.port = port; }
    public void setUsername(String username)              { this.username = username; }
    public void setPassword(String password)              { this.password = password; }
    public void setInputFolder(String inputFolder)        { this.inputFolder = inputFolder; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public void setDataTimeoutMs(int dataTimeoutMs)       { this.dataTimeoutMs = dataTimeoutMs; }
    public void setTrustManager(TrustManager trustManager){ this.trustManager = trustManager; }

    @Override
    public void upload(String objectCode, String remoteFileName, InputStream content, long contentLength) {
        // FTPSClient() with no args = explicit TLS (AUTH TLS)
        FTPSClient ftps = new FTPSClient();
        if (trustManager != null) {
            ftps.setTrustManager(trustManager);
        }
        try {
            ftps.setConnectTimeout(connectTimeoutMs);
            ftps.setDataTimeout(dataTimeoutMs);
            ftps.connect(host, port);
            if (!FTPReply.isPositiveCompletion(ftps.getReplyCode())) {
                throw new FunctionalException(
                        "FTPS connect refused by " + host + ":" + port + " — " + ftps.getReplyString().trim());
            }

            if (!ftps.login(username, password)) {
                throw new FunctionalException(
                        "FTPS login failed for user '" + username + "' on " + host + " — " + ftps.getReplyString().trim());
            }

            // Protect data channel
            ftps.execPBSZ(0);
            ftps.execPROT("P");

            ftps.enterLocalPassiveMode();
            ftps.setFileType(FTP.BINARY_FILE_TYPE);

            String objectDir = inputFolder.endsWith("/")
                    ? inputFolder + objectCode
                    : inputFolder + "/" + objectCode;
            ensureDirectory(ftps, objectDir);

            log.debug("FTPS upload started: {}:{}{}/{} ({} bytes)", host, port, objectDir, remoteFileName, contentLength);
            boolean stored = ftps.storeFile(objectDir + "/" + remoteFileName, content);
            if (!stored) {
                throw new TransientException(
                        "FTPS storeFile failed for " + remoteFileName + " — " + ftps.getReplyString().trim());
            }

            log.info("FTPS upload OK: object={} file={} size={} bytes", objectCode, remoteFileName, contentLength);

        } catch (IOException e) {
            throw new TransientException("FTPS I/O error during upload of " + remoteFileName + " on " + host, e);
        } finally {
            if (ftps.isConnected()) {
                try { ftps.logout(); }     catch (IOException ignored) {}
                try { ftps.disconnect(); } catch (IOException ignored) {}
            }
        }
    }

    private void ensureDirectory(FTPSClient ftps, String dir) throws IOException {
        if (ftps.changeWorkingDirectory(dir)) {
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String part : dir.split("/")) {
            if (part.isEmpty()) continue;
            current.append("/").append(part);
            String path = current.toString();
            if (!ftps.changeWorkingDirectory(path)) {
                ftps.makeDirectory(path);
                if (!ftps.changeWorkingDirectory(path)) {
                    throw new TransientException("Unable to create/access FTPS directory: " + path
                            + " — " + ftps.getReplyString().trim());
                }
            }
        }
    }
}