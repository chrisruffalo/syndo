package io.github.chrisruffalo.syndo.info;

import io.github.chrisruffalo.syndo.exceptions.RuntimeSyndoException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BuildProperties {

    private final static Properties buildProperties = loadBuildProperties();

    /**
     * Statically created so it can load at build time for Graal Native
     *
     * @return the loaded properties object
     */
    private static Properties loadBuildProperties() {
        final Properties props = new Properties();

        // look for build properties on path
        try (final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("build.properties")) {
            if (stream != null) {
                props.load(stream);
            }
        } catch (IOException e) {
            throw new RuntimeSyndoException("Could not load build properties", e);
        }

        return props;
    }

    private static String getProperty(final String key, final String defaultValue) {
        return buildProperties.getProperty(key, defaultValue);
    }

    public static String getVersion() {
        return getProperty("version", "0.0.0");
    }

}
