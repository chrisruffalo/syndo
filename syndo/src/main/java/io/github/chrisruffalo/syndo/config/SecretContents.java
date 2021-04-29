package io.github.chrisruffalo.syndo.config;

public class SecretContents {

    /**
     * The key of the secret value
     */
    private String key;

    /**
     * Path to the file (relative to the build file) that will serve
     * as the contents of the secret
     */
    private String file;

    /**
     * Contents of the file to use as the source for the secret
     */
    private String data;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
