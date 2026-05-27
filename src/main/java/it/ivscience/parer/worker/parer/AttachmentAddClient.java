package it.ivscience.parer.worker.parer;

import it.ivscience.parer.worker.parer.model.AttachmentAddRequest;
import it.ivscience.parer.worker.parer.model.AttachmentAddResponse;

/**
 * Client for the AggiuntaAllegatiSync operation (SACER sacer/AggiuntaAllegatiSync).
 * Wiring XML: parer-client-services.xml.
 */
public interface AttachmentAddClient {

    /**
     * Adds attachments to a Documentary Unit already acquired by SACER.
     * Synchronous call: the response contains the final result.
     */
    AttachmentAddResponse addAttachmentsSync(AttachmentAddRequest request);
}
