package it.ivscience.parer.worker.parer;

import it.ivscience.parer.worker.parer.model.NotificationRequest;
import it.ivscience.parer.worker.parer.model.NotificationResponse;

/**
 * Client for the NotificaTrasferimentoFile operation (SacerPing § 3.3.4).
 * Wiring XML: parer-client-services.xml.
 */
public interface NotificationClient {

    /**
     * Notifies ParER that the files have been deposited in the FTP folder.
     * Starts the pre-acquisition phase on the SACER side.
     */
    NotificationResponse notifyFileTransfer(NotificationRequest request);
}
