package it.ivscience.parer.worker.sip.builder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

/** Static DOM helpers shared across SIP section builders. */
final class DomUtil {

    private DomUtil() {}

    public static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create XML Document", e);
        }
    }

    public static Element textElement(Document doc, String tag, String text) {
        Element el = doc.createElement(tag);
        el.setTextContent(text);
        return el;
    }

    public static void appendText(Document doc, Element parent, String tag, String text) {
        parent.appendChild(textElement(doc, tag, text));
    }

    public static String serialize(Node node) {
        try {
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(node), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize XML node", e);
        }
    }

    public static String serialize(Document doc) {
        try {
            Transformer tf = TransformerFactory.newInstance().newTransformer();
            tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter sw = new StringWriter();
            tf.transform(new DOMSource(doc), new StreamResult(sw));
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize XML node", e);
        }
    }
}
