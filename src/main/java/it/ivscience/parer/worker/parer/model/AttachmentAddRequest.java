package it.ivscience.parer.worker.parer.model;

/**
 * Input parameters for the AggiuntaAllegatiSync SOAP WS.
 *
 * Fields:
 *  - environment:   SACER environment name (e.g. "UNIBO")
 *  - submitter:     submitter name (e.g. "DSpace-Bridge")
 *  - password:      submitter password
 *  - parentUnitKey: key of the parent Documentary Unit to attach files to
 *  - sipXml:        SIP Index XML for the attachments
 */
public class AttachmentAddRequest {

    private String environment;
    private String submitter;
    private String password;
    private String parentUnitKey;
    private String sipXml;

    public String getEnvironment()              { return environment; }
    public void setEnvironment(String v)        { this.environment = v; }

    public String getSubmitter()                { return submitter; }
    public void setSubmitter(String v)          { this.submitter = v; }

    public String getPassword()                 { return password; }
    public void setPassword(String v)           { this.password = v; }

    public String getParentUnitKey()            { return parentUnitKey; }
    public void setParentUnitKey(String v)      { this.parentUnitKey = v; }

    public String getSipXml()                   { return sipXml; }
    public void setSipXml(String v)             { this.sipXml = v; }
}
