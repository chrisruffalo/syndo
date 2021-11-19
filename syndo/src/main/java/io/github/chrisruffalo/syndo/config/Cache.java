package io.github.chrisruffalo.syndo.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configures the storage of the build system.
 */
public class Cache {

    /**
     * If this is set to true then the storage system is enabled. This requires the use of an
     * admission mutator to mutate builds. (This will be installed automatically.)
     */
    private boolean enabled = false;

    /**
     * Attempt to use a RWX volume claim to share storage so that all syndo builds
     * reuse the same storage. This allows the data to be shared by multiple pods.
     */
    private boolean shared = true;

    /**
     * When the storage is shared builds using this configuration will use the
     * given name. If you need to have multiple build configurations that share
     * with each other but <strong>not</strong> other build configurations then
     * each should have their own build name.
     */
    @JsonProperty("claim-name")
    private String claimName = "syndo-cache";

    /**
     * Allows setting a storage class on the PVC. If is null then no storage class will be set (which
     * will use the default storage class). If this value is "" then a blank value will be set on the
     * PVC which will override the default storage class with an empty (no) value.
     *
     * Note: this storage should be fast enough to be useful as a cache.
     */
    private String storageClass = null;

    /**
     * The volume to bind to. This is good if you have created a volume already and want to bind to it as
     * the shared volume. This is good for setting up builds across namespaces to use the same data. If this
     * value is null or empty then no volume name will be used.
     *
     * Note: this storage should be fast enough to be useful as a cache.
     */
    private String volumeName = null;

    /**
     * The size of the cache to request..
     */
    private String size = "10Gi";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getClaimName() {
        return claimName;
    }

    public void setClaimName(String claimName) {
        this.claimName = claimName;
    }

    public String getStorageClass() {
        return storageClass;
    }

    public void setStorageClass(String storageClass) {
        this.storageClass = storageClass;
    }

    public String getVolumeName() {
        return volumeName;
    }

    public void setVolumeName(String volumeName) {
        this.volumeName = volumeName;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }
}
