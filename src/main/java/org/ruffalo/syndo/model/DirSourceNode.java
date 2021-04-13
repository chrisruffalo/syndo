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
public class DirSourceNode extends BuildNode {

    private final String name;
    private Path directory;
    private BuildNode from;
    private String outputRef;
    private String hash;
    private boolean keep;

    public DirSourceNode(final String name) {
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

    public String getFullOutputRef() {
        if (this.hash != null && !this.hash.isEmpty() && !this.outputRef.contains(":")) {
            return outputRef + ":" + this.hash;
        }
        return outputRef;
    }

    public void setOutputRef(String outputRef) {
        this.outputRef = outputRef;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
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

    public String getFromRef() {
        if (this.from instanceof ImageRefSourceNode) {
            return ((ImageRefSourceNode)this.from).getImageRef();
        } else if(this.from instanceof DirSourceNode) {
            return ((DirSourceNode)this.from).getFullOutputRef();
        }
        return null;
    }
}
