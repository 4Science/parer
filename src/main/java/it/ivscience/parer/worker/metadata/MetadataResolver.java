package it.ivscience.parer.worker.metadata;

import it.ivscience.parer.worker.sip.SipIndexContext;

/**
 * Resolves source object metadata into a {@link SipIndexContext}
 * ready for SIP XML construction.
 *
 * Implementation:
 * - {@link impl.MetadataResolverDspaceRest} — "dspace" profile
 *
 * Wiring XML: metadata-services.xml.
 */
public interface MetadataResolver {

    /**
     * Resolves the metadata of the object identified by {@code sourceObjectId}.
     *
     * @param sourceObjectId identifier of the object in the source system
     *                       (e.g., DSpace item UUID, handle)
     * @return SIP context populated with all fields necessary for SIP Index construction
     * @throws MetadataResolutionException if resolution fails in a non-recoverable way
     */
    SipIndexContext resolve(String sourceObjectId);
}
