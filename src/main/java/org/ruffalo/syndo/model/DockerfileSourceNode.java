package org.ruffalo.syndo.model;

import java.util.ArrayList;
import java.util.List;

public class DockerfileSourceNode extends DirSourceNode {

    private String dockerfile;

    private List<String> dockerfileContents = new ArrayList<>();

    public DockerfileSourceNode(String name) {
        super(name);
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
    }

    public List<String> getDockerfileContents() {
        return dockerfileContents;
    }

    public void setDockerfileContents(List<String> dockerfileContents) {
        this.dockerfileContents = dockerfileContents;
    }
}
