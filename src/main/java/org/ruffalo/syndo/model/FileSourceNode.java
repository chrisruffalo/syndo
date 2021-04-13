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
    private Path directory;
    private BuildNode from;
    private String dockerfile;
    private String outputRef;
    private boolean keep;

    public FileSourceNode(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public String getOutputRef() {
        return outputRef;
    }

    public void setOutputRef(String outputRef) {
        this.outputRef = outputRef;
    }

    public boolean isKeep() {
        return keep;
    }

    public void setKeep(boolean keep) {
        this.keep = keep;
    }

    public BuildNode getFrom() {
        return from;
    }

    public void setFrom(BuildNode from) {
        this.from = from;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
    }

    public String getFromRef() {
        // cannot have a from ref and a dockerfile at the same time
        if (this.dockerfile != null && !this.dockerfile.isEmpty()) {
            return null;
        }
        if (this.from instanceof ImageRefSourceNode) {
            return ((ImageRefSourceNode)this.from).getImageRef();
        } else if(this.from instanceof FileSourceNode) {
            return ((FileSourceNode)this.from).getOutputRef();
        }
        return null;
    }
}
