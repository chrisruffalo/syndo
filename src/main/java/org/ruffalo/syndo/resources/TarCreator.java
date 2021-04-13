package org.ruffalo.syndo.resources;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class TarCreator {

    public static TarArchiveOutputStream createTar(final Path outputTarPath) throws IOException {
        final TarArchiveOutputStream tar = new TarArchiveOutputStream(new GzipCompressorOutputStream(Files.newOutputStream(outputTarPath)));
        tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
        tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        return tar;
    }

    public static void addToTar(final TarArchiveOutputStream tar, final Path fileToAdd, final String entryName) throws IOException {
        try (final InputStream stream = Files.newInputStream(fileToAdd)) {
            addToTar(tar, stream, Files.size(fileToAdd), entryName);
        }
    }

    public static void addToTar(final TarArchiveOutputStream tar, final byte[] contents, final String entryName) throws IOException {
        try (final InputStream stream = new ByteArrayInputStream(contents)) {
            addToTar(tar, stream, contents.length, entryName);
        }
    }

    public static void addToTar(final TarArchiveOutputStream tar, final InputStream fileToAdd, final long size, final String entryName) throws IOException {
        final TarArchiveEntry entry = new TarArchiveEntry(entryName);
        entry.setSize(size);
        tar.putArchiveEntry(entry);
        IOUtils.copy(fileToAdd, tar);
        tar.closeArchiveEntry();
    }

    public static void createResourceTar(final Path outputTarPath, final URL resourceUrl) throws URISyntaxException, IOException {
        Path folder = Resources.resourceToPath(resourceUrl);
        createDirectoryTar(outputTarPath, folder);
    }

    public static void createDirectoryTar(final Path outputTarPath, final Path rootDirectoryPath) throws IOException {
        // make sure the output tar is removed first for a clean re-write
        if (Files.exists(outputTarPath)) {
            Files.delete(outputTarPath);
        }

        try (
            final TarArchiveOutputStream tarArchiveOutputStream = createTar(outputTarPath)
        ) {
            addPrefixedDirectoryToTar(tarArchiveOutputStream, rootDirectoryPath, "");
        }
    }

    public static void addPrefixedDirectoryToTar(final TarArchiveOutputStream existingTar, final Path directoryPath, final String prefix) throws IOException {
        addPrefixedDirectoryToTar(existingTar, directoryPath, prefix, Collections.emptySet());
    }

    public static void addPrefixedDirectoryToTar(final TarArchiveOutputStream existingTar, final Path directoryPath, final String prefix, final Set<String> excludedFiles) throws IOException {
        Files.walkFileTree(directoryPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path entryPath = directoryPath.relativize(file);

                if (prefix != null && !prefix.isEmpty()) {
                    entryPath = Paths.get(prefix).resolve(entryPath);
                }

                final String entry = entryPath.toString();

                // filter out excluded files
                if (!excludedFiles.contains(entry)) {
                    // add entry name to tar
                    addToTar(existingTar, file, entry);
                }

                // continue file visit
                return super.visitFile(file, attrs);
            }
        });
    }

    public static String getMetaEnvContents(final Map<String, String> metaEnv) {
        final StringBuilder builder = new StringBuilder();
        metaEnv.forEach((key, value) -> {
            if(key == null) {
                return;
            }
            builder.append("export ");
            builder.append(key.toUpperCase().trim());
            builder.append("=");
            builder.append(value.trim());
            builder.append("\n");
        });
        return builder.toString().trim();
    }

    public static void addMetaEnvToTar(final TarArchiveOutputStream existingTar, final String metaPath, final Map<String, String> metaEnv) throws IOException {
        if (metaEnv == null || metaEnv.isEmpty()) {
            return;
        }

        final String metaEnvString = getMetaEnvContents(metaEnv);
        final TarArchiveEntry entry = new TarArchiveEntry(metaPath);
        entry.setSize(metaEnvString.length());
        existingTar.putArchiveEntry(entry);
        existingTar.write(metaEnvString.getBytes(StandardCharsets.UTF_8));
        existingTar.closeArchiveEntry();
    }



}
