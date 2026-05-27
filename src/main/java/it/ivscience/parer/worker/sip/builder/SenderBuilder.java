package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipSectionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.List;

import static it.ivscience.parer.worker.sip.builder.DomUtil.*;

/** Builds the {@code <Versatore>} fragment. All values injected via Spring (sip-services.xml). */
public class SenderBuilder implements SipSectionBuilder {

    private String environment;
    private String institution;
    private String structure;
    private String userId;

    private String tagVersatore;
    private String tagAmbiente;
    private String tagEnte;
    private String tagStruttura;
    private String tagUserId;

    public void setEnvironment(String v)   { this.environment  = v; }
    public void setInstitution(String v)   { this.institution  = v; }
    public void setStructure(String v)     { this.structure    = v; }
    public void setUserId(String v)        { this.userId       = v; }

    public void setTagVersatore(String v)  { this.tagVersatore = v; }
    public void setTagAmbiente(String v)   { this.tagAmbiente  = v; }
    public void setTagEnte(String v)       { this.tagEnte      = v; }
    public void setTagStruttura(String v)  { this.tagStruttura = v; }
    public void setTagUserId(String v)     { this.tagUserId    = v; }

    @Override
    public String getPlaceholder() { return "{SenderBuilder}"; }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        Document doc = newDocument();
        Element root = doc.createElement(tagVersatore);
        if (StringUtils.isNotBlank(environment)) {
            appendText(doc, root, tagAmbiente, environment);
        }
        if (StringUtils.isNotBlank(institution)) {
            appendText(doc, root, tagEnte, institution);
        }
        if (StringUtils.isNotBlank(structure)) {
            appendText(doc, root, tagStruttura, structure);
        }
        if (StringUtils.isNotBlank(userId)) {
            appendText(doc, root, tagUserId, userId);
        }
        return serialize(root);
    }
}
