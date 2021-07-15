package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.github.chrisruffalo.syndo.cmd.CommandBootstrap;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;

import java.nio.file.Files;
import java.nio.file.Path;

public class BootstrapBuilderAction extends SyndoBuilderAction {

    public static final String BOOTSTRAP_RESOURCE_PATH = "containers/syndo-runner";

    public static final String SYNDO_BUILDER = SYNDO + "-runner";

    private static final Path SYNDO_RESOURCE_PATH = bootstrapSyndoResourcePath(BOOTSTRAP_RESOURCE_PATH);

    @Override
    protected Path getResourcePath(BuildContext context) {
        final CommandBootstrap commandBootstrap = context.getCommandBuild() != null ? context.getCommandBuild() : context.getCommand().getBootstrap();

        Path bootstrapDirectory = commandBootstrap.getBootstrapRoot();
        if (bootstrapDirectory == null || !Files.exists(bootstrapDirectory)) {
            bootstrapDirectory = SYNDO_RESOURCE_PATH;
        } else {
            bootstrapDirectory = bootstrapDirectory.normalize().toAbsolutePath();
        }
        return bootstrapDirectory;
    }

    @Override
    protected String getImageName(final BuildContext context) {
        return SYNDO_BUILDER;
    }

    @Override
    protected String targetNamespace(BuildContext context) {
        return context.getNamespace();
    }

    @Override
    protected boolean isForced(BuildContext context) {
        final CommandBootstrap commandBootstrap = context.getCommandBuild() != null ? context.getCommandBuild() : context.getCommand().getBootstrap();
        return commandBootstrap.isForceBootstrap();
    }

    @Override
    protected Path outputTar(BuildContext context) {
        final CommandBootstrap commandBootstrap = context.getCommandBuild() != null ? context.getCommandBuild() : context.getCommand().getBootstrap();
        return commandBootstrap.getBootstrapTarOutput();
    }

    @Override
    protected void saveImageName(final BuildContext context, final String imageName) {
        context.setBuilderImageName(imageName);
    }
}
