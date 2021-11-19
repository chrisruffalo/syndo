package io.github.chrisruffalo.syndo.executions.actions;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.permissions.PermTuple;
import io.github.chrisruffalo.syndo.permissions.PermissionChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.FileSystem;
import java.util.List;

public abstract class BaseAction implements Action {

    private static final FileSystem memorySystem = Jimfs.newFileSystem(Configuration.unix());

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected FileSystem fs() {
        return memorySystem;
    }

    protected Logger logger() {
        return this.logger;
    }

    protected void permissionCheck(final OpenShiftClient client, final BuildContext context, final String namespace, PermTuple... tuples) {
        // check ability to create resources in context namespace
        final PermissionChecker localNamespaceChecker = new PermissionChecker(client, namespace);
        final List<PermTuple> missingPermissions = localNamespaceChecker.check(tuples);
        if (!missingPermissions.isEmpty()) {
            context.setStatus(BuildContext.Status.ERROR);
            missingPermissions.forEach(missing -> {
                logger().error("Missing permission '{} {}' in namespace '{}'", missing.getVerb(), missing.getResource(), client.getNamespace());
            });
        }
    }
}
