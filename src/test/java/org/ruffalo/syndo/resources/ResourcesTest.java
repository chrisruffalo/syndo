package org.ruffalo.syndo.resources;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourcesTest {

    @Test
    public void testResourceExport() throws URISyntaxException, IOException {
        final URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("export-test");
        final FileSystem exportSystem = Jimfs.newFileSystem(Configuration.unix());
        final Path exportPath = exportSystem.getPath("export-test");
        Resources.exportResourceDir(resourceUrl, exportPath);
        Assertions.assertTrue(Files.exists(exportSystem.getPath("export-test","test-resource.txt")));
        Assertions.assertTrue(Files.exists(exportSystem.getPath("export-test", "subdir","test-subresource.txt")));
    }

    @Test
    public void testSubResourceExport() throws URISyntaxException, IOException {
        final URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("export-test/subdir");
        final FileSystem exportSystem = Jimfs.newFileSystem(Configuration.unix());
        final Path exportPath = exportSystem.getPath("subdir");
        Resources.exportResourceDir(resourceUrl, exportPath);
        Assertions.assertTrue(Files.exists(exportSystem.getPath("subdir").resolve("test-subresource.txt")));
    }
}
