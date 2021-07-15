package io.github.chrisruffalo.syndo.cmd;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.github.chrisruffalo.syndo.cmd.converters.StringToPathConverter;

import java.nio.file.Path;

@Parameters(commandDescription = "Install the storage webhook service for augmenting Syndo builds with a storage cache. This requires the current user to have permissions to create cluster role bindings and mutating webhook configurations as well as deployment configurations, pods, and services.")
public class CommandCache extends CommandOpenShift {

    public static final String DEFAULT_CACHE_NAMESPACE = "syndo-infra";

    @Parameter(names={"--cache-resource-path", "-P"}, description = "Path to the artifacts to use for the Syndo storage webhook container image", converter = StringToPathConverter.class)
    private Path storageResourcePath;

    @Parameter(names={"--force-cache", "-F"}, description = "Setting this option to true forces the build of the Syndo cache webhook container even if the proper version is already present")
    private boolean forceCache;

    public CommandCache() {
        this.setNamespace(DEFAULT_CACHE_NAMESPACE);
    }

    public Path getStorageResourcePath() {
        return storageResourcePath;
    }

    public void setStorageResourcePath(Path storageResourcePath) {
        this.storageResourcePath = storageResourcePath;
    }

    public boolean isForceCache() {
        return forceCache;
    }

    public void setForceCache(boolean forceCache) {
        this.forceCache = forceCache;
    }
}
