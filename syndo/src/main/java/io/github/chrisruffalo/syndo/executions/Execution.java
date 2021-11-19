package io.github.chrisruffalo.syndo.executions;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.config.Loader;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.exceptions.SyndoException;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.impl.BootstrapExecution;
import io.github.chrisruffalo.syndo.executions.impl.BuildExecution;
import io.github.chrisruffalo.syndo.executions.impl.ExportExecution;
import io.github.chrisruffalo.syndo.executions.impl.NoCommandExecution;
import io.github.chrisruffalo.syndo.executions.impl.CacheExecution;
import io.github.chrisruffalo.syndo.executions.impl.VersionExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public abstract ExecutionResult execute();

    protected Logger logger() {
        return this.logger;
    }

    protected Root loadConfig(final BuildContext context, final Command command, Path pathToConfig) throws SyndoException {
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
        return Loader.read(pathToConfig, command.getProperties());
    }

    public static Execution get(Command command) {
        String parsed = command.getParsedCommand();
        if (parsed == null || parsed.isEmpty()) {
            parsed = "";
        }

        // create the appropriate execution based on the command
        Execution exe;
        switch (parsed.trim().toLowerCase()) {
            case "export":
                exe = new ExportExecution(command.getExport());
                break;
            case "build":
                exe = new BuildExecution(command);
                break;
            case "bootstrap":
                exe = new BootstrapExecution(command);
                break;
            case "version":
                exe = new VersionExecution();
                break;
            case "cache":
                exe = new CacheExecution(command);
                break;
            default:
                exe = new NoCommandExecution(command);
        }

        return exe;
    }

}
