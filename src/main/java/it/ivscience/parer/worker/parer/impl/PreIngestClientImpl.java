package it.ivscience.parer.worker.parer.impl;

import it.ivscience.parer.worker.parer.PreIngestClient;
import it.ivscience.parer.worker.parer.model.PreIngestRequest;
import it.ivscience.parer.worker.parer.model.PreIngestResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;

import java.util.Map;

import static java.util.Map.entry;


/**
 * Real implementation of {@link PreIngestClient} via raw SOAP over HTTP.
 * Calls POST preIngestEndpointUrl — InvioOggettoAsincrono (SacerPing).
 * Wiring XML: parer-client-services.xml (profile "!mock").
 */
public class PreIngestClientImpl extends AbstractParerSoapClient implements PreIngestClient {

    private static final Logger log = LogManager.getLogger(PreIngestClientImpl.class);

    private String preIngestEndpointUrl;

    public void setPreIngestEndpointUrl(String v) { this.preIngestEndpointUrl = v; }

    @Override
    public PreIngestResponse sendPreIngest(PreIngestRequest req) {
        log.info("InvioOggettoAsincrono → environment={} submitter={} objectKey={}",
                req.getEnvironment(), req.getSubmitter(), req.getObjectKey());

        String envelope = buildEnvelope(req);
        log.debug("Envelope: {}", envelope);
        String responseXml = sendSoap(preIngestEndpointUrl, "", envelope);
        return parseResponse(responseXml);
    }

    private static String buildEnvelope(PreIngestRequest req) {
        String tpl = loadTemplate("soap/pre-ingest.xml");
        return fillTemplate(tpl, Map.ofEntries(
                entry("nmAmbiente",          escapeXml(req.getEnvironment())),
                entry("nmVersatore",         escapeXml(req.getSubmitter())),
                entry("cdKeyObject",         escapeXml(req.getObjectKey())),
                entry("nmTipoObject",        escapeXml(req.getObjectType())),
                entry("flFileCifrato",       String.valueOf(req.isFileCiphered())),
                entry("flForzaWarning",      String.valueOf(req.isForceWarning())),
                entry("flForzaAccettazione", String.valueOf(req.isForceAcceptance()))
        ));
    }

    private static PreIngestResponse parseResponse(String xml) {
        Document doc = parseXml(xml);
        PreIngestResponse res = new PreIngestResponse();
        res.setOutcome(firstText(doc, "cdEsito"));
        res.setErrorCode(firstText(doc, "cdErr"));
        res.setErrorMessage(firstText(doc, "dsErr"));
        return res;
    }
}
