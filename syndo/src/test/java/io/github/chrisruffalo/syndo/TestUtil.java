package io.github.chrisruffalo.syndo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class TestUtil {

    public static Path getTestResourcePath(final String resourcePath) {
        try {
            return Paths.get(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource(resourcePath)).toURI());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
