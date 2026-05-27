package it.ivscience.parer.worker.parer.model;

/**
 * Entry in the deposited-file list for the NotificaTrasferimentoFile WS (§ 3.3.4).
 * Endpoint configured via {@code parer.soap.notifica-url}.
 */
public class DepositedFile {

    private String fileRole;
    private String fileName;
    private String encoding;

    public DepositedFile() {}

    public DepositedFile(String fileRole, String fileName) {
        this.fileRole = fileRole;
        this.fileName = fileName;
    }

    public String getFileRole()             { return fileRole; }
    public void setFileRole(String v)       { this.fileRole = v; }

    public String getFileName()             { return fileName; }
    public void setFileName(String v)       { this.fileName = v; }

    public String getEncoding()             { return encoding; }
    public void setEncoding(String v)       { this.encoding = v; }
}
