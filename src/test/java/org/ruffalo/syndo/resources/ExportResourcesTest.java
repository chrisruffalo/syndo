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

public class ExportResourcesTest {

    @Test
    public void testResourceExport() throws URISyntaxException, IOException {
        final URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("export-test");
        final FileSystem exportSystem = Jimfs.newFileSystem(Configuration.unix());
        final Path exportPath = exportSystem.getPath("export-test");
        ExportResources.exportResourceDir(resourceUrl, exportPath);
        Assertions.assertTrue(Files.exists(exportSystem.getPath("export-test","test-resource.txt")));
        Assertions.assertTrue(Files.exists(exportPath.resolve("subdir/test-subresource.txt")));
    }

    @Test
    public void testSubResourceExport() throws URISyntaxException, IOException {
        final URL resourceUrl = Thread.currentThread().getContextClassLoader().getResource("export-test/subdir");
        final FileSystem exportSystem = Jimfs.newFileSystem(Configuration.unix());
        final Path exportPath = exportSystem.getPath("subdir");
        ExportResources.exportResourceDir(resourceUrl, exportPath);
        Assertions.assertTrue(Files.exists(exportSystem.getPath("subdir/test-subresource.txt")));
    }
}
