package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipSectionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;

/**
 * Builds the full {@code <Annotazioni>} section, or returns empty string when
 * there are no annotation files. All values injected via Spring (sip-services.xml).
 */
public class AnnotationsBuilder implements SipSectionBuilder {

    private ComponentBuilder componente;

    private String tipoDocumento;
    private String tipoStruttura;
    private String tipoComponente;
    private String tipoSupporto;

    private String tagAnnotazioni;
    private String tagAnnotazione;
    private String tagIdDocumento;
    private String tagTipoDocumento;
    private String tagStrutturaOriginale;
    private String tagTipoStruttura;
    private String tagComponenti;

    public void setComponente(ComponentBuilder v)       { this.componente            = v; }
    public void setTipoDocumento(String v)              { this.tipoDocumento         = v; }
    public void setTipoStruttura(String v)              { this.tipoStruttura         = v; }
    public void setTipoComponente(String v)             { this.tipoComponente        = v; }
    public void setTipoSupporto(String v)               { this.tipoSupporto          = v; }
    public void setTagAnnotazioni(String v)             { this.tagAnnotazioni        = v; }
    public void setTagAnnotazione(String v)             { this.tagAnnotazione        = v; }
    public void setTagIdDocumento(String v)             { this.tagIdDocumento        = v; }
    public void setTagTipoDocumento(String v)           { this.tagTipoDocumento      = v; }
    public void setTagStrutturaOriginale(String v)      { this.tagStrutturaOriginale = v; }
    public void setTagTipoStruttura(String v)           { this.tagTipoStruttura      = v; }
    public void setTagComponenti(String v)              { this.tagComponenti         = v; }

    @Override
    public String getPlaceholder() { return "{AnnotationsBuilder}"; }

    private static void appendText(Document doc, Element parent, String tag, String text) {
        Element el = doc.createElement(tag);
        el.setTextContent(text);
        parent.appendChild(el);
    }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        List<SipFile> annotazioni = files.stream()
                .filter(f -> componente.isAnnotazione(f))
                .toList();
        if (annotazioni == null || annotazioni.isEmpty()) {
            return "";
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElement(tagAnnotazioni);
            doc.appendChild(root);

            for (SipFile sf : annotazioni) {
                Element ann = doc.createElement(tagAnnotazione);
                if (StringUtils.isNotBlank(sf.getFileName())) {
                    appendText(doc, ann, tagIdDocumento, sf.getFileName());
                }
                if (StringUtils.isNotBlank(tipoDocumento)) {
                    appendText(doc, ann, tagTipoDocumento, tipoDocumento);
                }

                if (StringUtils.isNotBlank(tipoStruttura)) {
                    Element struttura = doc.createElement(tagStrutturaOriginale);
                    appendText(doc, struttura, tagTipoStruttura, tipoStruttura);
                    Element compList = doc.createElement(tagComponenti);
                    compList.appendChild(componente.buildComponenteElement(doc, sf, 1, tipoComponente, tipoSupporto));
                    struttura.appendChild(compList);
                    ann.appendChild(struttura);
                    root.appendChild(ann);
                }
            }

            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            tf.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Annotazioni XML", e);
        }
    }
}
