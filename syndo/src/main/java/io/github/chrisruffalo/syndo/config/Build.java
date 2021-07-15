package io.github.chrisruffalo.syndo.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration information related to the build process itself.
 */
public class Build {

    /**
     * The name of a secret to use as a pull secret during the build.
     */
    @JsonProperty("pull-secret")
    private String pullSecret = null;

    /**
     * This is a map of labels to use on the build as node selectors to constrain
     * where the build can run.
     */
    @JsonProperty("node-selector")
    private Map<String, String> nodeSelector = new HashMap<>();

    public String getPullSecret() {
        return pullSecret;
    }

    public void setPullSecret(String pullSecret) {
        this.pullSecret = pullSecret;
    }

    public Map<String, String> getNodeSelector() {
        return nodeSelector;
    }

    public void setNodeSelector(Map<String, String> nodeSelector) {
        this.nodeSelector = nodeSelector;
    }
}
