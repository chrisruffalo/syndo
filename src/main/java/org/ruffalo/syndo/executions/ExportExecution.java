package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.build.SyndoBuiderAction;
import org.ruffalo.syndo.cmd.CommandExport;
import org.ruffalo.syndo.resources.ExportResources;
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
            ExportResources.exportResourceDir(Thread.currentThread().getContextClassLoader().getResource(SyndoBuiderAction.BOOTSTRAP_RESOURCE_PATH), path);
        } catch (URISyntaxException | IOException e) {
            logger.error("Could not export resources: {}", e.getMessage());
            result.setExitCode(1);
        }

        return result;
    }

}
