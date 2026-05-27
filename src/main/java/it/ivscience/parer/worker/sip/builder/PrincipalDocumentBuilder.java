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

/** Builds the content of {@code <DocumentoPrincipale>}. All values injected via Spring (sip-services.xml). */
public class PrincipalDocumentBuilder implements SipSectionBuilder {

    private ComponentBuilder componente;

    private String tipoDocumento;
    private String coarKey;
    private String tipoStruttura;
    private String tipoComponente;
    private String tipoSupporto;

    private String tagIdDocumento;
    private String tagTipoDocumento;
    private String tagProfiloDocumento;
    private String tagDescrizione;
    private String tagStrutturaOriginale;
    private String tagTipoStruttura;
    private String tagComponenti;

    public void setComponente(ComponentBuilder v)       { this.componente    = v; }
    public void setTipoDocumento(String v)              { this.tipoDocumento = v; }
    public void setCoarKey(String v)                    { this.coarKey       = v; }
    public void setTipoStruttura(String v)              { this.tipoStruttura         = v; }
    public void setTipoComponente(String v)             { this.tipoComponente        = v; }
    public void setTipoSupporto(String v)               { this.tipoSupporto          = v; }
    public void setTagIdDocumento(String v)             { this.tagIdDocumento        = v; }
    public void setTagTipoDocumento(String v)           { this.tagTipoDocumento      = v; }
    public void setTagProfiloDocumento(String v)        { this.tagProfiloDocumento   = v; }
    public void setTagDescrizione(String v)             { this.tagDescrizione        = v; }
    public void setTagStrutturaOriginale(String v)      { this.tagStrutturaOriginale = v; }
    public void setTagTipoStruttura(String v)           { this.tagTipoStruttura      = v; }
    public void setTagComponenti(String v)              { this.tagComponenti         = v; }

    @Override
    public String getPlaceholder() { return "{PrincipalDocumentBuilder}"; }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        List<SipFile> principale = files.stream()
                .filter(f -> !componente.isAnnotazione(f))
                .toList();
        if (principale == null || principale.isEmpty()) {
            return "";
        }

        String sipId = sipPackage.getSipId();
        Document doc = newDocument();

        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(sipId)) {
            sb.append(serialize(textElement(doc, tagIdDocumento, sipId)));
        }
        if (StringUtils.isNotBlank(tipoDocumento)) {
            sb.append(serialize(textElement(doc, tagTipoDocumento, tipoDocumento)));
        }

        String coar = coarKey != null ? context.get(coarKey) : null;
        if (StringUtils.isNotBlank(coar)) {
            Element profilo = doc.createElement(tagProfiloDocumento);
            appendText(doc, profilo, tagDescrizione, coar);
            sb.append(serialize(profilo));
        }

        if (StringUtils.isNotBlank(tipoStruttura)) {
            Element struttura = doc.createElement(tagStrutturaOriginale);
            appendText(doc, struttura, tagTipoStruttura, tipoStruttura);
            Element componenti = doc.createElement(tagComponenti);
            for (int i = 0; i < principale.size(); i++) {
                componenti.appendChild(
                    componente.buildComponenteElement(doc, principale.get(i), i + 1, tipoComponente, tipoSupporto));
            }
            struttura.appendChild(componenti);
            sb.append(serialize(struttura));
        }

        return sb.toString();
    }
}
