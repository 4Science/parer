package it.ivscience.parer.worker.parer.model;

/**
 * Input parameters for the InvioOggettoPreIngest SOAP WS (§ 3.3.1).
 */
public class PreIngestRequest {

    private String environment;
    private String submitter;
    private String objectKey;
    private String objectType;
    private boolean fileCiphered;
    private boolean forceWarning;
    private boolean forceAcceptance;

    public String getEnvironment()              { return environment; }
    public void setEnvironment(String v)        { this.environment = v; }

    public String getSubmitter()                { return submitter; }
    public void setSubmitter(String v)          { this.submitter = v; }

    public String getObjectKey()                { return objectKey; }
    public void setObjectKey(String v)          { this.objectKey = v; }

    public String getObjectType()               { return objectType; }
    public void setObjectType(String v)         { this.objectType = v; }

    public boolean isFileCiphered()             { return fileCiphered; }
    public void setFileCiphered(boolean v)      { this.fileCiphered = v; }

    public boolean isForceWarning()             { return forceWarning; }
    public void setForceWarning(boolean v)      { this.forceWarning = v; }

    public boolean isForceAcceptance()          { return forceAcceptance; }
    public void setForceAcceptance(boolean v)   { this.forceAcceptance = v; }

}
