package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.exceptions.SyndoException;
import io.github.chrisruffalo.syndo.executions.OpenShiftExecution;
import io.github.chrisruffalo.syndo.executions.actions.Action;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.impl.BootstrapBuilderAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.BuildPrepareAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.BuildResolveAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.ComponentBuildAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.ComponentFilterAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.CreateTarAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.HashFilterAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.ManageSecretsAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.PermissionCheckAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.CacheEnableAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.SyndoBuilderAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.VerifyAction;

import java.util.LinkedList;
import java.util.List;

/**
 * An execution object is created and configured for an execution. It needs the details necessary to build
 * an OpenShift client and the location of the build yaml file.
 */
public class BuildExecution extends OpenShiftExecution {

    private final Command command;

    public BuildExecution(final Command command) {
        this.command = command;
    }

    @Override
    protected List<Action> getBuildActions(BuildContext context) {
        final List<Action> actions = new LinkedList<>();

        // verify configuration
        final VerifyAction verifyAction = new VerifyAction();
        actions.add(verifyAction);

        // check connection to openshift
        final PermissionCheckAction permissionCheckAction = new PermissionCheckAction();
        actions.add(permissionCheckAction);

        final SyndoBuilderAction syndoBuildAction = new BootstrapBuilderAction();
        actions.add(syndoBuildAction);

        // also prepare storage if enabled (this just installs the webhook CRD and _not_ the service behind it)
        if (context.getConfig() != null && context.getConfig().getStorage() != null && context.getConfig().getStorage().isEnabled()) {
            final CacheEnableAction mutatingWebhookAction = new CacheEnableAction();
            actions.add(mutatingWebhookAction);
        }

        // prepare build
        final BuildPrepareAction prepareAction = new BuildPrepareAction();
        actions.add(prepareAction);

        // resolve artifacts inputs/outputs and build order from dependencies
        final BuildResolveAction resolveAction = new BuildResolveAction();
        actions.add(resolveAction);

        final ComponentFilterAction componentFilterAction = new ComponentFilterAction();
        actions.add(componentFilterAction);

        // add when not forcing the build
        if (!this.command.getBuild().isForce()) {
            final HashFilterAction filterAction = new HashFilterAction();
            actions.add(filterAction);
        }

        // create build tar
        final CreateTarAction createTarAction = new CreateTarAction();
        actions.add(createTarAction);

        // ensure secrets are present for build
        final ManageSecretsAction manageSecretsAction = new ManageSecretsAction();
        actions.add(manageSecretsAction);

        final ComponentBuildAction componentBuildAction = new ComponentBuildAction();
        actions.add(componentBuildAction);

        return actions;
    }

    @Override
    public CommandOpenShift getOpenShiftCommand() {
        return this.command.getBuild();
    }

    @Override
    public BuildContext createContext() throws SyndoException {
        final BuildContext context = new BuildContext();
        context.setCommand(this.command);
        context.setCommandBuild(this.command.getBuild());

        // find and load configuration
        final Root config = this.loadConfig(context, this.command, this.command.getBuild().getBuildFile());
        context.setConfig(config);

        return context;
    }
}
