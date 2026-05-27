package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipSectionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;

import java.util.List;

import static it.ivscience.parer.worker.sip.builder.DomUtil.*;

/** Builds the content of {@code <Chiave>}. All values injected via Spring (sip-services.xml). */
public class KeyBuilder implements SipSectionBuilder {

    private String numberKey;
    private String yearKey;
    private String registryTypeKey;

    private String tagNumero;
    private String tagAnno;
    private String tagTipoRegistro;

    public void setNumberKey(String v)       { this.numberKey       = v; }
    public void setYearKey(String v)         { this.yearKey         = v; }
    public void setRegistryTypeKey(String v) { this.registryTypeKey = v; }

    public void setTagNumero(String v)       { this.tagNumero       = v; }
    public void setTagAnno(String v)         { this.tagAnno         = v; }
    public void setTagTipoRegistro(String v) { this.tagTipoRegistro = v; }

    @Override
    public String getPlaceholder() { return "{KeyBuilder}"; }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        String number = context.get(numberKey);
        String year   = context.get(yearKey);
        String type   = context.get(registryTypeKey);

        StringBuilder sb = new StringBuilder();
        Document doc  = newDocument();
        if (StringUtils.isNotBlank(number)) {
            sb.append(serialize(textElement(doc, tagNumero, number)));
        }
        if (StringUtils.isNotBlank(year)) {
            sb.append(serialize(textElement(doc, tagAnno, year)));
        }
        if (StringUtils.isNotBlank(type)) {
            sb.append(serialize(textElement(doc, tagTipoRegistro, type)));
        }

        return sb.toString();
    }
}
