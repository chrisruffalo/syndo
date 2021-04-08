package org.ruffalo.syndo.resources;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;

public class SyndoTarCreator {

    public static void createResourceTar(final Path outputTarPath, final URL resourceUrl) throws URISyntaxException, IOException {
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
        createDirectoryTar(outputTarPath, folder);
    }

    public static void createDirectoryTar(final Path outputTarPath, final Path rootDirectoryPath) throws IOException {
        // make sure the output tar is removed first for a clean re-write
        if (Files.exists(outputTarPath)) {
            Files.delete(outputTarPath);
        }

        try (
            final OutputStream rawOutputStream = Files.newOutputStream(outputTarPath);
            final TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(new GzipCompressorOutputStream(rawOutputStream))
        ) {
            tarArchiveOutputStream.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tarArchiveOutputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            Files.walkFileTree(rootDirectoryPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    final Path entryPath = rootDirectoryPath.relativize(file);
                    final TarArchiveEntry entry = new TarArchiveEntry(entryPath.toString());
                    entry.setSize(Files.size(file));
                    tarArchiveOutputStream.putArchiveEntry(entry);
                    IOUtils.copy(Files.newInputStream(file), tarArchiveOutputStream);
                    tarArchiveOutputStream.closeArchiveEntry();
                    return super.visitFile(file, attrs);
                }
            });
        }
    }


}
