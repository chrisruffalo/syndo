package io.github.chrisruffalo.syndo.config;

import java.util.List;

public class Secret {

    /**
     * The name of the secret both in the config file and the secret
     * name in the target namespace
     */
    private String name;

    /**
     * The type of the secret like 'kubernetes.io/dockerconfigjson'
     */
    private String type;

    /**
     * Contents of the secret
     */
    private List<SecretContents> contents;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<SecretContents> getContents() {
        return contents;
    }

    public void setContents(List<SecretContents> contents) {
        this.contents = contents;
    }
}
