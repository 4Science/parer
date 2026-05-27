package it.ivscience.parer.worker.sip;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the SIP XML Index by loading a template and delegating each
 * placeholder to the matching {@link SipSectionBuilder} Spring bean.
 *
 * Template path is configurable via {@code parer.sip.template-path}
 * (default: {@code classpath:sip/parer_sip_index_template.xml}).
 * Wiring XML: sip-services.xml.
 */
public class SipBuilder {

    private List<SipSectionBuilder> sectionBuilders;
    private String templatePath;

    public void setSectionBuilders(List<SipSectionBuilder> sectionBuilders) {
        this.sectionBuilders = sectionBuilders;
    }

    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }

    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        try {
            String xml = loadTemplate();
            for (SipSectionBuilder b : sectionBuilders) {
                xml = xml.replace(b.getPlaceholder(), b.build(sipPackage, files, context));
            }
            return xml;
        } catch (Exception e) {
            throw new RuntimeException("Error building SIP XML for sipId=" + sipPackage.getSipId(), e);
        }
    }

    /** Strips UTF-8 BOM (﻿) and leading whitespace — both break XML declaration detection. */
    private static String stripPreamble(String s) {
        if (s.startsWith("﻿")) s = s.substring(1);
        return s.stripLeading();
    }

    private String loadTemplate() throws IOException {
        String path = templatePath.startsWith("classpath:")
                ? templatePath.substring("classpath:".length())
                : templatePath;
        if (templatePath.startsWith("classpath:")) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) throw new IOException("SIP template not found: " + templatePath);
                return stripPreamble(new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        try (InputStream is = new java.io.FileInputStream(path)) {
            return stripPreamble(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
