package io.github.chrisruffalo.syndo.config;

import io.github.chrisruffalo.syndo.exceptions.SyndoException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class LoaderTest {

    @Test
    public void testLoad() throws URISyntaxException, SyndoException {
        final Path path = Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("sample-build.yml")).toURI());
        Assertions.assertTrue(Files.exists(path));
        final Root config = Loader.read(path);
        Assertions.assertEquals(4, config.getComponents().size());
        Assertions.assertEquals(1, config.getAliases().size());
    }

    @Test
    public void testLoadWithProperties() throws URISyntaxException, SyndoException {

        final Map<String, String> testProperties = new HashMap<>();
        testProperties.put("root", "root-root");
        testProperties.put("root-parent", "${root}");

        final Path path = Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource("sample-build.yml")).toURI());
        final Root config = Loader.read(path, testProperties);

        Assertions.assertEquals("root-root", config.getComponents().get(3).getPath());
    }

}
