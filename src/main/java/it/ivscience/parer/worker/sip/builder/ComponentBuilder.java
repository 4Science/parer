package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/**
 * Shared Spring bean: builds {@code <Componente>} XML fragments and classifies files.
 * Tag names and FileClassifier are injected via Spring (sip-services.xml).
 */
public class ComponentBuilder {

    private FileClassifier fileClassifier;
    private String tagComponente;
    private String tagId;
    private String tagOrdine;
    private String tagTipoComponente;
    private String tagTipoSupportoComponente;
    private String tagNomeComponente;
    private String tagFormatoFileVersato;
    private String tagHashVersato;

    public void setFileClassifier(FileClassifier v)     { this.fileClassifier            = v; }
    public void setTagComponente(String v)              { this.tagComponente             = v; }
    public void setTagId(String v)                      { this.tagId                     = v; }
    public void setTagOrdine(String v)                  { this.tagOrdine                 = v; }
    public void setTagTipoComponente(String v)          { this.tagTipoComponente         = v; }
    public void setTagTipoSupportoComponente(String v)  { this.tagTipoSupportoComponente = v; }
    public void setTagNomeComponente(String v)          { this.tagNomeComponente         = v; }
    public void setTagFormatoFileVersato(String v)      { this.tagFormatoFileVersato     = v; }
    public void setTagHashVersato(String v)             { this.tagHashVersato            = v; }

    public boolean isAnnotazione(SipFile sf) {
        return fileClassifier.isAnnotazione(sf.getFileName());
    }

    /** Builds a DOM Element for the Componente node. Caller must pass the owning Document. */
    public Element buildComponenteElement(Document doc, SipFile sf, int order,
                                          String tipoComponenteValue, String tipoSupportoValue) {
        Element el = doc.createElement(tagComponente);
        appendText(doc, el, tagId,                     sf.getFileName());
        appendText(doc, el, tagOrdine,                 String.valueOf(order));
        appendText(doc, el, tagTipoComponente,         tipoComponenteValue);
        appendText(doc, el, tagTipoSupportoComponente, tipoSupportoValue);
        appendText(doc, el, tagNomeComponente,         sf.getFileName());
        appendText(doc, el, tagFormatoFileVersato,     extractExtension(sf.getFileName()));
        if (sf.getChecksum() != null && !sf.getChecksum().isBlank()) {
            appendText(doc, el, tagHashVersato, sf.getChecksum());
        }
        return el;
    }

    public String buildComponente(SipFile sf, int order, String tipoComponenteValue, String tipoSupportoValue) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            doc.appendChild(buildComponenteElement(doc, sf, order, tipoComponenteValue, tipoSupportoValue));
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Componente XML", e);
        }
    }

    private static void appendText(Document doc, Element parent, String tag, String text) {
        Element el = doc.createElement(tag);
        el.setTextContent(text);
        parent.appendChild(el);
    }

    static String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toUpperCase() : "";
    }
}
