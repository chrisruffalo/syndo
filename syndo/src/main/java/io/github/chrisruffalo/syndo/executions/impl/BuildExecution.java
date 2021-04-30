package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.impl.BuildPrepareAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.HashFilterAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.ManageSecretsAction;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.config.Loader;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.exceptions.SyndoException;
import io.github.chrisruffalo.syndo.executions.OpenShiftExecution;
import io.github.chrisruffalo.syndo.executions.actions.Action;
import io.github.chrisruffalo.syndo.executions.actions.impl.BuildResolveAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.ComponentBuildAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.ComponentFilterAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.CreateTarAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.SyndoBuilderAction;
import io.github.chrisruffalo.syndo.executions.actions.impl.VerifyAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * An execution object is created and configured for an execution. It needs the details necessary to build
 * an OpenShift client and the location of the build yaml file.
 */
public class BuildExecution extends OpenShiftExecution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

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

        final SyndoBuilderAction syndoBuildAction = new SyndoBuilderAction();
        actions.add(syndoBuildAction);

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

        // find configuration
        Path pathToConfig = this.command.getBuild().getBuildFile();
        // try and resolve path to configuration if it is not absolute
        if (!pathToConfig.isAbsolute()) {
            // try and resolve relative to working dir
            final Path workingDir = Paths.get(System.getProperty("user.dir"));
            final Path workingDirResolved = workingDir.resolve(pathToConfig);
            if (!Files.exists(pathToConfig) && Files.exists(workingDirResolved)) {
                pathToConfig = workingDirResolved;
            }
        }
        if (!Files.exists(pathToConfig)) {
            throw new SyndoException(String.format("No build yaml could be found at path: %s", pathToConfig));
        }
        pathToConfig = pathToConfig.normalize().toAbsolutePath();
        context.setConfigPath(pathToConfig);

        // load syndo root configuration
        Root config = Loader.read(pathToConfig, command.getProperties());
        context.setConfig(config);

        return context;
    }
}
