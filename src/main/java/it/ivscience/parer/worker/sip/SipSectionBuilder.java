package it.ivscience.parer.worker.sip;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;

import java.util.List;

public interface SipSectionBuilder {

    /** Placeholder token in the XML template, e.g. {@code {SenderBuilder}}. */
    String getPlaceholder();

    /** Returns an XML fragment (no XML declaration) to substitute the placeholder. */
    String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context);
}
