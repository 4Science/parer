package it.ivscience.parer.worker.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordered registry of pipeline status names. All status strings come from
 * Spring XML (worker-services.xml) — no Java constants.
 *
 * Steps/services inject this bean and use the named getters to set or compare
 * statuses without hardcoding any string in Java code.
 * Wiring: worker-services.xml.
 */
public class SipStatusRegistry {

    private List<String> statuses = new ArrayList<>();

    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }

    /** Returns true if {@code current} appears strictly after {@code reference} in the ordered list. */
    public boolean isAfter(String current, String reference) {
        int ci = statuses.indexOf(current);
        int ri = statuses.indexOf(reference);
        return ci > 0 && ri >= 0 && ci > ri;
    }
}
