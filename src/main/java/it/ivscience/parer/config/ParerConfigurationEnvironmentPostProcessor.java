package it.ivscience.parer.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads the application configuration following the DSpace pattern:
 *   config/parer.cfg  — default configuration, on the classpath (committed)
 *
 * Priority is (from highest to lowest):
 *   environment variables and system properties > parer.cfg
 *
 * Activates Spring profiles from spring.profiles.active in parer.cfg
 * so that XML profiles (<beans profile="mock">) are respected.
 *
 * Registered via META-INF/spring.factories.
 */
public class ParerConfigurationEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LogManager.getLogger(ParerConfigurationEnvironmentPostProcessor.class);

    private static final String CONFIG_DIR = System.getenv("CONFIG_DIR");
    private static final String MAIN_CONFIG = CONFIG_DIR + File.separator + "parer.cfg";

    private static final String SOURCE_MAIN  = "parerConfig";

    /**
     * Executed before Spring Boot post-processors so that
     * spring.profiles.active is available when the context is built.
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources sources = environment.getPropertySources();

        // 1. Load parer.cfg (mandatory, classpath)
        // retrieve env property
        Properties mainProps = load(MAIN_CONFIG);
        sources.addLast(new PropertiesPropertySource(SOURCE_MAIN, mainProps));
        log.debug("Loaded {}", MAIN_CONFIG);

        // 2. Activate Spring profiles declared in parer.cfg
        String profilesValue = null;
        if (profilesValue == null) {
            profilesValue = mainProps.getProperty("spring.profiles.active");
        }
        if (profilesValue != null && !profilesValue.isBlank()) {
            for (String profile : profilesValue.split(",")) {
                String trimmed = profile.trim();
                if (!trimmed.isEmpty()) {
                    environment.addActiveProfile(trimmed);
                    log.debug("Spring profile activated: {}", trimmed);
                }
            }
        }
    }

    private Properties loadRequired(Resource resource) {
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Mandatory configuration not found: " + resource.getDescription());
        }
        return load(resource);
    }

    private Properties loadOptional(Resource resource) {
        if (!resource.exists()) return null;
        return load(resource);
    }

    private Properties load(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return load(is);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Error loading configuration: " + resource.getDescription(), e);
        }
    }

    private Properties load(String file) {
        try (FileInputStream is = new FileInputStream(file)) {
            return load(is);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Error loading configuration: " + file, e);
        }
    }

    private Properties load(InputStream inputStream) throws IOException {
        Properties props = new Properties();
        props.load(inputStream);
        return props;
    }

    @Override
    public int getOrder() {
        // Executed very early: before ConfigDataEnvironmentPostProcessor
        // (DSpace convention: HIGHEST_PRECEDENCE + 10)
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}