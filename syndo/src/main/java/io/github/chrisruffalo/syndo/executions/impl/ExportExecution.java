package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.cmd.CommandExport;
import io.github.chrisruffalo.syndo.executions.Execution;
import io.github.chrisruffalo.syndo.executions.ExecutionResult;
import io.github.chrisruffalo.syndo.executions.actions.impl.BootstrapBuilderAction;
import io.github.chrisruffalo.syndo.resources.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;

public class ExportExecution extends Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CommandExport cmd;

    public ExportExecution(final CommandExport cmd) {
        this.cmd = cmd;
    }

    public ExecutionResult execute() {
        final ExecutionResult result = new ExecutionResult();

        try {
            Path path = this.cmd.getBootstrapRoot();
            if (path != null) {
                path = path.normalize().toAbsolutePath();
            }
            logger.info("Exporting resources to: {}", path);
            Resources.exportResourceDir(Thread.currentThread().getContextClassLoader().getResource(BootstrapBuilderAction.BOOTSTRAP_RESOURCE_PATH), path);
        } catch (URISyntaxException | IOException e) {
            logger.error("Could not export resources: {}", e.getMessage());
            result.setExitCode(1);
        }

        return result;
    }

}
