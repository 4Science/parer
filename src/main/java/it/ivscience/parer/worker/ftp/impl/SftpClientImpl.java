package it.ivscience.parer.worker.ftp.impl;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;
import it.ivscience.parer.worker.ftp.FtpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;

/**
 * SFTP implementation of {@link FtpClient} via JSch.
 *
 * Streams content directly from S3 to SFTP — no local file buffering.
 * Thread-safe: each upload creates its own JSch Session and ChannelSftp.
 *
 * Note: StrictHostKeyChecking is disabled by default for convenience in internal
 * deployments. For production hardening, configure a known_hosts file via
 * {@link #setKnownHostsFile(String)}.
 *
 * Wiring XML: ftp-services.xml (profile "sftp &amp; !mock").
 */
public class SftpClientImpl implements FtpClient {

    private static final Logger log = LogManager.getLogger(SftpClientImpl.class);

    private String host;
    private int    port             = 22;
    private String username;
    private String password;
    private String inputFolder;
    private int    connectTimeoutMs = 15_000;
    private String knownHostsFile   = null;

    public void setHost(String host)                      { this.host = host; }
    public void setPort(int port)                         { this.port = port; }
    public void setUsername(String username)              { this.username = username; }
    public void setPassword(String password)              { this.password = password; }
    public void setInputFolder(String inputFolder)        { this.inputFolder = inputFolder; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public void setKnownHostsFile(String knownHostsFile)  { this.knownHostsFile = (knownHostsFile != null && !knownHostsFile.isBlank()) ? knownHostsFile : null; }

    @Override
    public void upload(String objectCode, String remoteFileName, InputStream content, long contentLength) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp sftp = null;
        try {
            if (knownHostsFile != null) {
                jsch.setKnownHosts(knownHostsFile);
            }

            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            if (knownHostsFile == null) {
                session.setConfig("StrictHostKeyChecking", "no");
            }
            session.connect(connectTimeoutMs);

            sftp = (ChannelSftp) session.openChannel("sftp");
            sftp.connect(connectTimeoutMs);

            String objectDir = inputFolder.endsWith("/")
                    ? inputFolder + objectCode
                    : inputFolder + "/" + objectCode;
            ensureDirectory(sftp, objectDir);

            log.debug("SFTP upload started: {}:{}{}/{} ({} bytes)", host, port, objectDir, remoteFileName, contentLength);
            sftp.put(content, objectDir + "/" + remoteFileName, ChannelSftp.OVERWRITE);
            log.info("SFTP upload OK: object={} file={} size={} bytes", objectCode, remoteFileName, contentLength);

        } catch (JSchException e) {
            if (e.getMessage() != null && (e.getMessage().contains("Auth fail") || e.getMessage().contains("USERAUTH"))) {
                throw new FunctionalException("SFTP authentication failed for user '" + username + "' on " + host, e);
            }
            throw new TransientException("SFTP connection error during upload of " + remoteFileName + " on " + host, e);
        } catch (SftpException e) {
            throw new TransientException("SFTP transfer error for " + remoteFileName + ": " + e.getMessage(), e);
        } finally {
            if (sftp != null && sftp.isConnected()) sftp.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private void ensureDirectory(ChannelSftp sftp, String dir) throws SftpException {
        try {
            sftp.cd(dir);
            return;
        } catch (SftpException ignored) {}
        // Directory does not exist; create path component by component
        StringBuilder current = new StringBuilder();
        for (String part : dir.split("/")) {
            if (part.isEmpty()) continue;
            current.append("/").append(part);
            String path = current.toString();
            try {
                sftp.mkdir(path);
            } catch (SftpException ignored) {} // already exists
            sftp.cd(path);
        }
    }
}
