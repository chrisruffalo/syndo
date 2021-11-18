package io.github.chrisruffalo.syndo.config;

/**
 * Re-usable shell for when pull secrets are used.
 */
public class PullSecretRef {

    /**
     * The name of the secret in k8s. This secret must be of type "kubernetes.io/dockerconfigjson" and
     * provide the data in the key ".dockerconfigjson". If the pull secret is an old-style "dockercfg" type
     * then it should be handled gracefully.
     */
    private String name;

    /**
     * The key in the secret to use as the json file
     */
    private String key = ".dockerconfigjson";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
