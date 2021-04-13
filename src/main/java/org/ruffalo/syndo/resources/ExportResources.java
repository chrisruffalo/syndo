package org.ruffalo.syndo.resources;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

public class ExportResources {

    public static Path resourceToPath(final URL resourceUrl) throws URISyntaxException, IOException {
        Path folder;
        try {
            folder = Paths.get(resourceUrl.toURI());
        } catch (FileSystemNotFoundException fex) {
            FileSystem fs = FileSystems.newFileSystem(resourceUrl.toURI(), new HashMap<>());
            final String resourceUrlToString = resourceUrl.toString();
            final int subPathIndex = resourceUrlToString.indexOf("!");
            if (subPathIndex >= 0 && subPathIndex + 1 < resourceUrlToString.length()) {
                String path = resourceUrlToString.substring(subPathIndex + 1);
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                folder = fs.getPath(path);
            } else {
                folder = fs.getPath("/");
            }
        }
        return folder;
    }

    public static void exportResourceDir(final URL resourceURL, final Path outputPath) throws URISyntaxException, IOException {
        final Path folder = resourceToPath(resourceURL);
        // create path
        Files.createDirectories(outputPath);
        // walk and copy
        Files.walkFileTree(folder, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                final Path relativePath = folder.relativize(file);
                final Path targetPath = outputPath.resolve(relativePath.toString());
                Files.createDirectories(targetPath.getParent());
                Files.copy(file, targetPath);

                // continue file visit
                return super.visitFile(file, attrs);
            }
        });
    }

}
