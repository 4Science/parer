package it.ivscience.parer.worker.parer.model;

/**
 * Output parameters of the NotificaTrasferimentoFile SOAP WS (§ 3.3.4).
 * Endpoint configured via {@code parer.soap.notifica-url}.
 */
public class NotificationResponse {

    private String outcome;
    private String errorCode;
    private String errorMessage;

    public boolean isOk() {
        return "OK".equalsIgnoreCase(outcome);
    }

    public String getOutcome()              { return outcome; }
    public void setOutcome(String v)        { this.outcome = v; }

    public String getErrorCode()            { return errorCode; }
    public void setErrorCode(String v)      { this.errorCode = v; }

    public String getErrorMessage()         { return errorMessage; }
    public void setErrorMessage(String v)   { this.errorMessage = v; }
}
