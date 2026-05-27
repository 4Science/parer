package it.ivscience.parer.worker.parer.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Input parameters for the NotificaTrasferimentoFile SOAP WS (§ 3.3.4).
 * Endpoint configured via {@code parer.soap.notifica-url}.
 */
public class NotificationRequest {

    private String environment;
    private String submitter;
    private String objectKey;
    private List<DepositedFile> depositedFiles = new ArrayList<>();

    public String getEnvironment()                          { return environment; }
    public void setEnvironment(String v)                    { this.environment = v; }

    public String getSubmitter()                            { return submitter; }
    public void setSubmitter(String v)                      { this.submitter = v; }

    public String getObjectKey()                            { return objectKey; }
    public void setObjectKey(String v)                      { this.objectKey = v; }

    public List<DepositedFile> getDepositedFiles()          { return depositedFiles; }
    public void setDepositedFiles(List<DepositedFile> v)    { this.depositedFiles = v; }
}
