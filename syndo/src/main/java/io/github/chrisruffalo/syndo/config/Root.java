package io.github.chrisruffalo.syndo.config;

import com.fasterxml.jackson.annotation.JsonRootName;

import java.util.LinkedList;
import java.util.List;

/**
 * The root configuration object wraps all the sections of the config file.
 *
 */
@JsonRootName("syndo")
public class Root {

    /**
     * Build configuration
     */
    private Build build = new Build();

    /**
     * Storage configuration
     */
    private Cache cache = new Cache();

    /**
     * A list of components that comprise the possible build targets for a single build.
     */
    private List<Component> components = new LinkedList<>();

    /**
     * Aliases are a mapping of names to components so that different build
     * sub-units can be invoked without needing to type every component name explicitly.
     */
    private List<Alias> aliases = new LinkedList<>();

    /**
     * Secrets that are managed (inserted/updated) by syndo.
     */
    private List<Secret> secrets = new LinkedList<>();

    public List<Component> getComponents() {
        return components;
    }

    public void setComponents(List<Component> components) {
        this.components = components;
    }

    public List<Alias> getAliases() {
        return aliases;
    }

    public void setAliases(List<Alias> aliases) {
        this.aliases = aliases;
    }

    public List<Secret> getSecrets() {
        return secrets;
    }

    public void setSecrets(List<Secret> secrets) {
        this.secrets = secrets;
    }

    public Build getBuild() {
        return build;
    }

    public void setBuild(Build build) {
        this.build = build;
    }

    public Cache getStorage() {
        return cache;
    }

    public void setStorage(Cache cache) {
        this.cache = cache;
    }
}
