package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipSectionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;

import java.time.LocalDate;
import java.util.List;

import static it.ivscience.parer.worker.sip.builder.DomUtil.*;

/** Builds the content of {@code <ProfiloUnitaDocumentaria>}. All values injected via Spring (sip-services.xml). */
public class UnitBuilder implements SipSectionBuilder {

    private String subjectKey;
    private String dateKey;

    private String tagOggetto;
    private String tagData;

    public void setSubjectKey(String v) { this.subjectKey = v; }
    public void setDateKey(String v)    { this.dateKey    = v; }

    public void setTagOggetto(String v) { this.tagOggetto = v; }
    public void setTagData(String v)    { this.tagData    = v; }

    @Override
    public String getPlaceholder() { return "{UnitBuilder}"; }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        String subject = context.get(subjectKey);
        String date    = context.get(dateKey);
        Document doc   = newDocument();
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(subject)) {
            sb.append(serialize(textElement(doc, tagOggetto, subject)));
        }
        if (StringUtils.isNotBlank(date)) {
            sb.append(serialize(textElement(doc, tagData, date)));
        }
        return sb.toString();
    }
}
