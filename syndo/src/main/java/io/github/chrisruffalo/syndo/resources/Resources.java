package io.github.chrisruffalo.syndo.resources;

import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Resources {

    public static String hashURL(final URL sourceUrl) throws URISyntaxException, IOException {
        return hashPath(resourceToPath(sourceUrl));
    }

    /**
     * Produces a consistent hash of the source path by finding all of the files in the path, then sorting
     * the files by name, and then hashing the name and the contents of each file. The file name is included
     * because moving a file should trigger a rebuild for correctness even if nothing else changes.
     *
     * By performing this the same way each time the files will be hashed in the same order unless a new
     * file is added which would change the hash. If the files do not change it should always produce the
     * same output.
     *
     * @param sourcePath the source path to digest if a single file the file will be hashed, if a directory the entire tree will be hashed
     * @param salts inputs to salt the data with so that it changes if something else outside of the dir changes
     * @return a hex encoded string representing the SHA-512 hash of the target path/directory tree
     * @throws IOException when an IO exception occurs while reading underlying files/directories
     */
    public static String hashPath(final Path sourcePath, String... salts) throws IOException {
        Map<String, Path> filePaths = new HashMap<>();
        if (Files.isDirectory(sourcePath)) {
            Files.walkFileTree(sourcePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    filePaths.put(file.toString(), file);
                    // continue file visit
                    return super.visitFile(file, attrs);
                }
            });
        } else {
            filePaths.put(sourcePath.toString(), sourcePath);
        }

        final List<String> pathKeys = new ArrayList<>(filePaths.keySet());
        Collections.sort(pathKeys);

        MessageDigest digest;
        try {
             digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        for (final String salt : salts) {
            if (salt != null && !salt.isEmpty()) {
                digest.update(salt.getBytes(StandardCharsets.UTF_8));
            }
        }

        for (final String pathKey : pathKeys) {
            final Path path = filePaths.get(pathKey);
            if (path == null) {
                continue;
            }
            // first file name
            digest.update(path.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            // then hash of file
            digest.update(Files.readAllBytes(path));
        }

        // return digest
        return Hex.encodeHexString(digest.digest());
    }

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
                if (!path.startsWith(fs.getSeparator())) {
                    path = fs.getSeparator() + path;
                }
                folder = fs.getPath(path);
            } else {
                folder = fs.getPath(fs.getSeparator());
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
            Path targetPath = outputPath.normalize();
            // resolving the path piecewise like this allows us to step through
            // each path element as a string value and not get caught out by
            // the changing of path separators between filesystems (or jimfs)
            for (final Path path : relativePath) {
                targetPath = targetPath.resolve(path.toString());
            }
            Files.createDirectories(targetPath.getParent());
            Files.copy(file, targetPath);

            // continue file visit
            return super.visitFile(file, attrs);
            }
        });
    }

}
