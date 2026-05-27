package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipSectionBuilder;

import java.util.List;

/** Returns the annotation file count as a plain string for {@code <NumeroAnnotazioni>}. */
public class CountAnnotationsBuilder implements SipSectionBuilder {

    private ComponentBuilder componente;

    public void setComponente(ComponentBuilder v) { this.componente = v; }

    @Override
    public String getPlaceholder() { return "{CountAnnotationsBuilder}"; }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        long count = files.stream().filter(f -> componente.isAnnotazione(f)).count();
        return String.valueOf(count);
    }
}
