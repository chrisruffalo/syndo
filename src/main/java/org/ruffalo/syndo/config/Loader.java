package org.ruffalo.syndo.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    public static Root read(final Path yaml) {
        // return empty root if no configuration given or the configuration file does not exist
        if (yaml == null || !Files.exists(yaml)) {
            return new Root();
        }

        try {
            return mapper().readValue(Files.newInputStream(yaml), Root.class);
        } catch (IOException e) {
            logger.error("Could not load Syndo configuration: {}", e.getMessage());
            return new Root();
        }
    }

}
