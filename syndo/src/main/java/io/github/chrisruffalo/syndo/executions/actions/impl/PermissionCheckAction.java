package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.executions.actions.BaseAction;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.permissions.PermTuple;
import io.github.chrisruffalo.syndo.permissions.PermissionChecker;

import java.util.List;

public class PermissionCheckAction extends BaseAction {

    @Override
    public void execute(BuildContext context) {
        final CommandOpenShift commandOpenShift = context.getCommandBuild() != null ? context.getCommandBuild() : context.getCommand().getBootstrap();
        if (commandOpenShift == null) {
            logger().warn("Permission Checks should not be performed outside of an OpenShift context");
            return;
        }

        // get the openshift client
        final OpenShiftClient client = context.getClient();
        if (client == null) {
            logger().error("No OpenShift client present in context");
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }

        final String namespace = context.getNamespace();

        // create new permission checker
        final PermissionChecker checker = new PermissionChecker(client, namespace);

        // these things are required by syndo builds
        final List<PermTuple> missingPermissions = checker.check(
            new PermTuple("create", "buildconfigs"),
            new PermTuple("create", "build"),
            new PermTuple("create", "imagestream"),
            new PermTuple("create", "imagestreamtags")
        ) ;

        // report on missing permissions
        if (!missingPermissions.isEmpty()) {
            context.setStatus(BuildContext.Status.ERROR);
            missingPermissions.forEach(missing -> {
                logger().error("Missing permission '{} {}' in namespace '{}'", missing.getVerb(), missing.getResource(), namespace);
            });
        }
    }
}
