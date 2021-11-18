package io.github.chrisruffalo.syndo.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A component is a single unit of build for Syndo. It must define a unique name
 * and a base image ('from' or 'dockerfile') as well as the source directory that
 * the artifacts are in.
 *
 * Optionally it may define a 'to' element that defines the output of the image
 * but if none is specified then it will use the component name as the output image
 * name.
 */
public class Component {

    /**
     * Component name unique to the build. Serves as the identifier of
     * the component.
     */
    private String name;

    /**
     * The source image layer for the component
     */
    private String from;

    /**
     * Optionally a relative path to the dockerfile within the
     * build path that will be used to build the image.
     */
    private String dockerfile;

    /**
     * Optionally a name for the output image if it is different
     * than the component name.
     */
    private String to;

    /**
     * A path to the build resources. If the path is not absolute it
     * will be resolved relative to the build file.
     */
    private String path;

    /**
     * A path to the build script. This is optional but defaults to "build.sh".
     * This is the entrypoint for the individual component build.
     */
    private String script;

    /**
     * Set the storage type. Defaults to "overlay". If not set to "vfs" will default
     * back to "overlay".
     */
    private String storage = "overlay";

    /**
     * Transient images are NOT pushed to the target registry. They are only used within
     * the context of the build. If the image is not contained within the build then it
     * will be added.
     *
     * This is good for images that are likely to change every build but that are used
     * to layer other containers on. This saves storage in the registry and takes less time.
     * This means, however, that they aren't cached so that if child layers depend on them
     * they need to be rebuilt often.
     */
    @JsonProperty("transient")
    private boolean transientImage;

    /**
     * Allow an individual image to also have a particular pull secret.
     */
    @JsonProperty("pull-secret")
    private PullSecretRef pullSecretRef = new PullSecretRef();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getDockerfile() {
        return dockerfile;
    }

    public void setDockerfile(String dockerfile) {
        this.dockerfile = dockerfile;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getStorage() {
        if (storage == null || !storage.trim().toLowerCase().equals("vfs")) {
            return "overlay";
        }
        return "vfs";
    }

    public void setStorage(String storage) {
        this.storage = storage;
    }

    public boolean isTransientImage() {
        return transientImage;
    }

    public void setTransientImage(boolean transientImage) {
        this.transientImage = transientImage;
    }

    public PullSecretRef getPullSecretRef() {
        return pullSecretRef;
    }

    public void setPullSecretRef(PullSecretRef pullSecretRef) {
        this.pullSecretRef = pullSecretRef;
    }
}
