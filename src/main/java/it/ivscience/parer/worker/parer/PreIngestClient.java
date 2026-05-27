package it.ivscience.parer.worker.parer;

import it.ivscience.parer.worker.parer.model.PreIngestRequest;
import it.ivscience.parer.worker.parer.model.PreIngestResponse;

/**
 * Client for the InvioOggettoPreIngest operation (SacerPing § 3.3.1).
 * Wiring XML: parer-client-services.xml.
 */
public interface PreIngestClient {

    /**
     * Sends the SIP Index to ParER (PING) for preliminary validation.
     * If the result is OK, the object enters the IN_ATTESA_FILE state on the ParER side.
     */
    PreIngestResponse sendPreIngest(PreIngestRequest request);
}
