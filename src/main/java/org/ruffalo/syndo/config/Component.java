package org.ruffalo.syndo.config;

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
}
