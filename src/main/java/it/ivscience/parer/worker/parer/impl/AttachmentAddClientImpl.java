package it.ivscience.parer.worker.parer.impl;

import it.ivscience.parer.worker.parer.AttachmentAddClient;
import it.ivscience.parer.worker.parer.model.AttachmentAddRequest;
import it.ivscience.parer.worker.parer.model.AttachmentAddResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import java.util.Map;

/**
 * Real implementation of {@link AttachmentAddClient} via raw SOAP over HTTP.
 * Calls POST attachmentAddUrl — AggiuntaAllegatiSync (SACER).
 * Wiring XML: parer-client-services.xml (profile "!mock").
 */
public class AttachmentAddClientImpl extends AbstractParerSoapClient implements AttachmentAddClient {

    private static final Logger log = LogManager.getLogger(AttachmentAddClientImpl.class);

    private String attachmentAddUrl;

    public void setAttachmentAddUrl(String v) { this.attachmentAddUrl = v; }

    @Override
    public AttachmentAddResponse addAttachmentsSync(AttachmentAddRequest req) {
        log.info("AggiuntaAllegatiSync → environment={} submitter={} parentUnitKey={}",
                req.getEnvironment(), req.getSubmitter(), req.getParentUnitKey());

        String envelope = buildEnvelope(req);
        String responseXml = sendSoap(attachmentAddUrl, attachmentAddUrl, envelope);
        return parseResponse(responseXml);
    }

    private static String buildEnvelope(AttachmentAddRequest req) {
        String tpl = loadTemplate("soap/attachment-add.xml");
        return fillTemplate(tpl, Map.of(
                "nmAmbiente",         escapeXml(req.getEnvironment()),
                "nmVersatore",        escapeXml(req.getSubmitter()),
                "cdPassword",         escapeXml(req.getPassword()),
                "cdKeyObjectUdPadre", escapeXml(req.getParentUnitKey()),
                "xmlSip",             req.getSipXml() != null ? req.getSipXml() : ""
        ));
    }

    private static AttachmentAddResponse parseResponse(String xml) {
        Document doc = parseXml(xml);
        AttachmentAddResponse res = new AttachmentAddResponse();
        res.setOutcome(firstText(doc, "cdEsito"));
        res.setErrorCode(firstText(doc, "cdErr"));
        res.setErrorMessage(firstText(doc, "dlErr"));
        return res;
    }
}
