package it.ivscience.parer.worker.parer.impl;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.core.SoapActionCallback;
import org.springframework.xml.transform.StringResult;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public abstract class AbstractParerSoapClient {

    private WebServiceTemplate webServiceTemplate;

    public void setWebServiceTemplate(WebServiceTemplate v) { this.webServiceTemplate = v; }

    protected String sendSoap(String endpointUrl, String soapAction, String payloadXml) {
        try {
            StringResult result = new StringResult();
            webServiceTemplate.sendSourceAndReceiveToResult(
                    endpointUrl,
                    new StringSource(payloadXml),
                    new SoapActionCallback(soapAction),
                    result);
            return result.toString();
        } catch (Exception e) {
            throw new SoapCallException("SOAP call to " + endpointUrl + " failed: " + e.getMessage(), e);
        }
    }

    protected static String escapeXml(String s) {
        return StringEscapeUtils.escapeXml11(s);
    }

    protected static String loadTemplate(String resourcePath) {
        try {
            return new ClassPathResource(resourcePath)
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SoapCallException("Cannot load SOAP template: " + resourcePath, e);
        }
    }

    protected static String fillTemplate(String template, Map<String, String> values) {
        String result = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    protected static Document parseXml(String xml) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new SoapCallException("Failed to parse SOAP response: " + e.getMessage(), e);
        }
    }

    protected static String firstText(Document doc, String localName) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) {
            nodes = doc.getElementsByTagName(localName);
        }
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    public static class SoapCallException extends RuntimeException {
        public SoapCallException(String message, Throwable cause) { super(message, cause); }
    }
}
