package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.actions.Action;
import org.ruffalo.syndo.actions.BuildContext;
import org.ruffalo.syndo.actions.BuildPrepareAction;
import org.ruffalo.syndo.actions.BuildResolveAction;
import org.ruffalo.syndo.actions.ComponentBuildAction;
import org.ruffalo.syndo.actions.ComponentFilterAction;
import org.ruffalo.syndo.actions.CreateTarAction;
import org.ruffalo.syndo.actions.HashFilterAction;
import org.ruffalo.syndo.actions.SyndoBuilderAction;
import org.ruffalo.syndo.actions.VerifyAction;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.cmd.CommandOpenShift;
import org.ruffalo.syndo.config.Loader;
import org.ruffalo.syndo.config.Root;
import org.ruffalo.syndo.exceptions.SyndoException;
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
        Root config = Loader.read(pathToConfig);
        context.setConfig(config);

        return context;
    }
}
