package it.ivscience.parer.worker.sip.builder;

import it.ivscience.parer.content.SipFile;
import it.ivscience.parer.content.SipPackage;
import it.ivscience.parer.worker.sip.SipIndexContext;
import it.ivscience.parer.worker.sip.SipSectionBuilder;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;

import java.util.List;

import static it.ivscience.parer.worker.sip.builder.DomUtil.*;

/** Builds the dynamic content of {@code <DatiSpecifici>}. All values injected via Spring (sip-services.xml). */
public class SpecificDataBuilder implements SipSectionBuilder {

    private String collectionIdKey;
    private String collectionDescriptionKey;
    private String dataProviderKey;

    private String tagCollezioneId;
    private String tagCollezioneDescrizione;
    private String tagDataProvider;

    public void setCollectionIdKey(String v)          { this.collectionIdKey          = v; }
    public void setCollectionDescriptionKey(String v) { this.collectionDescriptionKey = v; }
    public void setDataProviderKey(String v)          { this.dataProviderKey          = v; }

    public void setTagCollezioneId(String v)          { this.tagCollezioneId          = v; }
    public void setTagCollezioneDescrizione(String v) { this.tagCollezioneDescrizione = v; }
    public void setTagDataProvider(String v)          { this.tagDataProvider          = v; }

    @Override
    public String getPlaceholder() { return "{SpecificDataBuilder}"; }

    @Override
    public String build(SipPackage sipPackage, List<SipFile> files, SipIndexContext context) {
        Document doc = newDocument();

        String collectionId = context.get(collectionIdKey);
        String collectionDescription = context.get(collectionDescriptionKey);
        String dataProvider = context.get(dataProviderKey);

        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(collectionId)) {
            sb.append(serialize(textElement(doc, tagCollezioneId, collectionId)));
        }
        if (StringUtils.isNotBlank(collectionDescription)) {
            sb.append(serialize(textElement(doc, tagCollezioneDescrizione, collectionDescription)));
        }
        if (StringUtils.isNotBlank(dataProvider)) {
            sb.append(serialize(textElement(doc, tagDataProvider, dataProvider)));
        }
        return sb.toString();
    }
}
