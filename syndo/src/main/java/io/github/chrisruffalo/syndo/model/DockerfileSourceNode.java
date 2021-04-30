package io.github.chrisruffalo.syndo.model;

import io.github.chrisruffalo.syndo.config.Component;

import java.util.ArrayList;
import java.util.List;

public class DockerfileSourceNode extends DirSourceNode {

    private String dockerfile;

    private List<String> dockerfileContents = new ArrayList<>();

    public DockerfileSourceNode(final Component component) {
        super(component);
        this.setDockerfile(component.getDockerfile());
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
