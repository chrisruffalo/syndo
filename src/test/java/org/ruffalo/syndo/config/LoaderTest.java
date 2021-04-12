package org.ruffalo.syndo.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class LoaderTest {

    @Test
    public void testLoad() throws URISyntaxException {
        final Path path = Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("sample-build.yml")).toURI());
        Assertions.assertTrue(Files.exists(path));
        final Root config = Loader.read(path);
        Assertions.assertEquals(4, config.getComponents().size());
        Assertions.assertEquals(1, config.getAliases().size());
    }

}
