package it.ivscience.parer.worker.parer.impl;

import it.ivscience.parer.worker.parer.NotificationClient;
import it.ivscience.parer.worker.parer.model.DepositedFile;
import it.ivscience.parer.worker.parer.model.NotificationRequest;
import it.ivscience.parer.worker.parer.model.NotificationResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import java.util.Map;

/**
 * Real implementation of {@link NotificationClient} via raw SOAP over HTTP.
 * Calls POST notificationEndpointUrl — NotificaTrasferimento (SacerPing).
 * Wiring XML: parer-client-services.xml (profile "!mock").
 */
public class NotificationClientImpl extends AbstractParerSoapClient implements NotificationClient {

    private static final Logger log = LogManager.getLogger(NotificationClientImpl.class);

    private String notificationEndpointUrl;

    public void setNotificationEndpointUrl(String v) { this.notificationEndpointUrl = v; }

    @Override
    public NotificationResponse notifyFileTransfer(NotificationRequest req) {
        log.info("NotificaTrasferimento → environment={} submitter={} objectKey={} nFile={}",
                req.getEnvironment(), req.getSubmitter(), req.getObjectKey(),
                req.getDepositedFiles().size());

        String envelope = buildEnvelope(req);
        String responseXml = sendSoap(notificationEndpointUrl, "", envelope);
        //log.debug("Response: {}", responseXml);
        return parseResponse(responseXml);
    }

    private static String buildEnvelope(NotificationRequest req) {
        String fileTpl = loadTemplate("soap/file-depositato.xml");
        StringBuilder files = new StringBuilder();
        for (DepositedFile fd : req.getDepositedFiles()) {
            files.append(fillTemplate(fileTpl, Map.of(
                    "nmTipoFile", escapeXml(fd.getFileRole()),
                    "nmNomeFile", escapeXml(fd.getFileName())
            )));
        }
        String tpl = loadTemplate("soap/notification.xml");
        return fillTemplate(tpl, Map.of(
                "nmAmbiente",          escapeXml(req.getEnvironment()),
                "nmVersatore",         escapeXml(req.getSubmitter()),
                "cdKeyObject",         escapeXml(req.getObjectKey()),
                "listaFileDepositati", files.toString()
        ));
    }

    private static NotificationResponse parseResponse(String xml) {
        Document doc = parseXml(xml);
        NotificationResponse res = new NotificationResponse();
        res.setOutcome(firstText(doc, "cdEsito"));
        res.setErrorCode(firstText(doc, "cdErr"));
        res.setErrorMessage(firstText(doc, "dsErr"));
        return res;
    }
}
