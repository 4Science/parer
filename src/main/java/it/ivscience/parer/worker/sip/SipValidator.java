package it.ivscience.parer.worker.sip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

/**
 * Validates the SIP XML Index against the WSRequestUnico1_5.xsd XSD.
 *
 * XSD loading strategy (in order of precedence):
 * 1. Absolute path on disk, if xsdPath starts with "/" (legacy filesystem override)
 * 2. Classpath resource (default: xsd/WSRequestUnico1_5.xsd, or classpath:xsd/...)
 * If no resource is available, validation is skipped with a warning.
 *
 * XML Wiring: sip-services.xml — xsdPath injected via <property>.
 */
public class SipValidator {

    private static final Logger log = LogManager.getLogger(SipValidator.class);

    /** XSD Path: absolute filesystem or classpath (with or without classpath: prefix). */
    private String xsdPath = "xsd/WSRequestUnico1_5.xsd";

    public void setXsdPath(String xsdPath) { this.xsdPath = xsdPath; }

    /**
     * Validates the XML. Throws {@link SipValidationException} in case of a blocking error.
     */
    public void validate(String xml, String sipId) {
        Schema schema = loadSchema(sipId);
        if (schema == null) return;

        try {
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xml)));
            log.debug("XSD validation OK for sipId={}", sipId);

        } catch (SAXException e) {
            throw new SipValidationException("XSD validation failed for sipId=" + sipId + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error during XSD validation for sipId=" + sipId, e);
        }
    }

    private Schema loadSchema(String sipId) {
        if (xsdPath == null || xsdPath.isBlank()) {
            log.warn("xsdPath not configured: structural validation skipped for sipId={}", sipId);
            return null;
        }

        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        // 1. Absolute filesystem (path starts with "/" or "C:\")
        if (xsdPath.startsWith("/") || (xsdPath.length() > 1 && xsdPath.charAt(1) == ':')) {
            File xsdFile = new File(xsdPath);
            if (!xsdFile.exists()) {
                log.warn("XSD file on disk not found: {} — validation skipped for sipId={}", xsdPath, sipId);
                return null;
            }
            try {
                return sf.newSchema(xsdFile);
            } catch (SAXException e) {
                log.warn("Unable to load XSD from disk {}: {} — validation skipped", xsdPath, e.getMessage());
                return null;
            }
        }

        // 2. Classpath (with or without "classpath:" prefix)
        String resourcePath = xsdPath.startsWith("classpath:") ? xsdPath.substring("classpath:".length()) : xsdPath;
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("XSD classpath resource not found: {} — validation skipped for sipId={}", xsdPath, sipId);
            return null;
        }
        try (InputStream is = resource.getInputStream()) {
            return sf.newSchema(new StreamSource(is));
        } catch (SAXException | IOException e) {
            log.warn("Unable to load XSD from classpath {}: {} — validation skipped", xsdPath, e.getMessage());
            return null;
        }
    }
}
