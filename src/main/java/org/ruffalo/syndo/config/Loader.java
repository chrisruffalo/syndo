package org.ruffalo.syndo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.ruffalo.syndo.exceptions.SyndoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * This is the helper class that provides a common way to read a configuration yaml.
 */
public class Loader {

    private static final Logger logger = LoggerFactory.getLogger(Loader.class);
    private static ObjectMapper mapper = null;

    private static ObjectMapper mapper() {
        if (mapper == null) {
            synchronized (logger) {
                if (mapper == null) {
                    mapper = new ObjectMapper(new YAMLFactory());

                    // this allows us to use the "syndo" root element
                    mapper.configure(DeserializationFeature.UNWRAP_ROOT_VALUE, true);

                    // these allow for more lenient parsing
                    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false);
                    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                }
            }
        }
        return mapper;
    }

    /**
     * Given a path to a YAML file, read that YAML file as a Root object.
     *
     * @param yaml the path to the yaml file
     * @return if the configuration file can be found an object representing the config file will be returned,
     *         otherwise if the file is missing or not correct a null configuration object will be returned.
     */
    public static Root read(final Path yaml) throws SyndoException  {
        // return empty root if no configuration given or the configuration file does not exist
        if (yaml == null) {
            throw new SyndoException("Cannot load yaml file for a null path");
        }

        if (!Files.exists(yaml)) {
            throw new SyndoException(String.format("No configuration yaml found at path %s", yaml));
        }

        try {
            return mapper().readValue(Files.newInputStream(yaml), Root.class);
        } catch (IOException e) {
            throw new SyndoException(String.format("Could not load Syndo configuration file %s: %s", yaml, e.getMessage()));
        }
    }

}
