package it.ivscience.parer.services;

/**
 * DSpace-style configuration service.
 * Encapsulates property reading from the source chain
 * (parer.cfg → local.cfg → environment variables).
 *
 * The API is a subset of org.dspace.services.ConfigurationService,
 * compatible with the DSpace 7.x pattern.
 */
public interface ConfigurationService {

    String getProperty(String name);

    String getProperty(String name, String defaultValue);

    int getIntProperty(String name, int defaultValue);

    long getLongProperty(String name, long defaultValue);

    boolean getBooleanProperty(String name, boolean defaultValue);

    String[] getArrayProperty(String name);

    boolean hasProperty(String name);
}