package org.ruffalo.syndo.model;

import java.nio.file.Path;

/**
 * A file source node is what connects the custom build strategy used by Syndo to the local file system. It requires
 * a "FROM" image just as any docker build would (which is itself another build node) and it has an output ref
 * (which is where it will be committed/pushed).
 *
 * Another way to think of this is the preparation of local files to be built (tasks) that will be sent to the actual
 * syndo build image.
 */
public class FileSourceNode extends BuildNode {

    private final String name;
    private final Path directory;
    private final BuildNode from;
    private final String outputRef;

    public FileSourceNode(final String name, final BuildNode from, final Path directory, final String outputRef) {
        this.name = name;
        this.from = from;
        this.directory = directory;
        this.outputRef = outputRef;
    }

    @Override
    public BuildNode from() {
        return this.from;
    }
}
