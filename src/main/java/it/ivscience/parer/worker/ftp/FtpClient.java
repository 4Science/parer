package it.ivscience.parer.worker.ftp;

import it.ivscience.parer.worker.errors.FunctionalException;
import it.ivscience.parer.worker.errors.TransientException;

import java.io.InputStream;

/**
 * FTP/SFTP Client for file transfer to ParER.
 * <p>
 * FTP structure expected by ParER (§ 3.3.3.1):
 * <pre>
 * root/
 *  └─ &lt;sacer_ping.ftp.input_folder&gt;/
 *      └─ &lt;objectCode&gt;/
 *          ├─ &lt;objectCode&gt;_file1.ext
 *          └─ &lt;objectCode&gt;_file2.ext
 * </pre>
 */
public interface FtpClient {

    /**
     * Transfers a single file to the object's FTP folder.
     *
     * @param objectCode     unique object code (= CdKeyObject)
     * @param remoteFileName file name on the FTP server (e.g., {@code objectCode_master.tif})
     * @param content        content stream — NOT loaded into memory
     * @param contentLength  file size in bytes
     */
    void upload(String objectCode, String remoteFileName, InputStream content, long contentLength) throws FunctionalException, TransientException;
}