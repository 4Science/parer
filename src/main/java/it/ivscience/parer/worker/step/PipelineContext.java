package it.ivscience.parer.worker.step;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.model.WorkUnitDescriptor;
import it.ivscience.parer.worker.parer.model.DepositedFile;
import it.ivscience.parer.worker.sip.SipIndexContext;

import java.util.ArrayList;
import java.util.List;

public class PipelineContext {

    private final WorkUnitDescriptor msg;
    private final String sipId;
    private SipPackage sipPackage;
    private SipIndexContext metadataContext;
    private String parerKey;
    private String dirPrefix;
    private String sipXml;
    private String sipXmlFileName;
    private List<SipFile> files;
    private List<DepositedFile> depositedFiles = new ArrayList<>();

    public PipelineContext(WorkUnitDescriptor msg) {
        this.msg    = msg;
        this.sipId  = msg.getObjectId();
    }

    public WorkUnitDescriptor getMsg()                    { return msg; }
    public String getSipId()                              { return sipId; }
    public SipPackage getSipPackage()                     { return sipPackage; }
    public void setSipPackage(SipPackage v)               { this.sipPackage = v; }
    public SipIndexContext getMetadataContext()            { return metadataContext; }
    public void setMetadataContext(SipIndexContext v)      { this.metadataContext = v; }
    public String getParerKey()                           { return parerKey; }
    public void setParerKey(String v)                     { this.parerKey = v; }
    public String getDirPrefix()                          { return dirPrefix; }
    public void setDirPrefix(String v)                    { this.dirPrefix = v; }
    public String getSipXml()                             { return sipXml; }
    public void setSipXml(String v)                       { this.sipXml = v; }
    public String getSipXmlFileName()                     { return sipXmlFileName; }
    public void setSipXmlFileName(String v)               { this.sipXmlFileName = v; }
    public List<SipFile> getFiles()                       { return files; }
    public void setFiles(List<SipFile> v)                 { this.files = v; }
    public List<DepositedFile> getDepositedFiles()        { return depositedFiles; }
    public void setDepositedFiles(List<DepositedFile> v)  { this.depositedFiles = v; }
}