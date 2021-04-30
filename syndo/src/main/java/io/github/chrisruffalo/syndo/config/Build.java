package io.github.chrisruffalo.syndo.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration information related to the build process itself.
 */
public class Build {

    @JsonProperty("pull-secret")
    private String pullSecret = null;

    public String getPullSecret() {
        return pullSecret;
    }

    public void setPullSecret(String pullSecret) {
        this.pullSecret = pullSecret;
    }
}
