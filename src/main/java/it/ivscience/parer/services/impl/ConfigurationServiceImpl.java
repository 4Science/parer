package it.ivscience.parer.services.impl;

import it.ivscience.parer.services.ConfigurationService;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * DSpace-style implementation of {@link ConfigurationService}.
 * Lightweight delegate on the Spring Environment, which aggregates:
 *   environment variables > local.cfg > parer.cfg
 * (loaded by ParerConfigurationEnvironmentPostProcessor).
 */
public class ConfigurationServiceImpl implements ConfigurationService, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public String getProperty(String name) {
        return environment.getProperty(name);
    }

    @Override
    public String getProperty(String name, String defaultValue) {
        return environment.getProperty(name, defaultValue);
    }

    @Override
    public int getIntProperty(String name, int defaultValue) {
        String val = environment.getProperty(name);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public long getLongProperty(String name, long defaultValue) {
        String val = environment.getProperty(name);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean getBooleanProperty(String name, boolean defaultValue) {
        String val = environment.getProperty(name);
        if (val == null || val.isBlank()) return defaultValue;
        return Boolean.parseBoolean(val.trim());
    }

    @Override
    public String[] getArrayProperty(String name) {
        String val = environment.getProperty(name);
        if (val == null || val.isBlank()) return new String[0];
        return val.split(",");
    }

    @Override
    public boolean hasProperty(String name) {
        return environment.containsProperty(name);
    }
}