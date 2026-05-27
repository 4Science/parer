package it.ivscience.parer.worker.sip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generic metadata container for SIP Index construction.
 * Keys are defined by the producer (MetadataResolver) and consumer (SipSectionBuilder) — not here.
 */
public class SipIndexContext {

    private final List<Metadata> metadata = new ArrayList<>();
    private String metadataXml;

    public void setMetadataXml(String v) { this.metadataXml = v; }
    public String getMetadataXml()       { return metadataXml; }

    public void add(Metadata m) {
        metadata.add(m);
    }

    public String get(String name) {
        return metadata.stream()
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .map(Metadata::getValue)
                .orElse(null);
    }

    public List<Metadata> getAll() {
        return Collections.unmodifiableList(metadata);
    }
}
