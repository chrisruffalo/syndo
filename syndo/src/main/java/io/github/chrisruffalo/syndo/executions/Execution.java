package io.github.chrisruffalo.syndo.executions;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.executions.impl.BootstrapExecution;
import io.github.chrisruffalo.syndo.executions.impl.BuildExecution;
import io.github.chrisruffalo.syndo.executions.impl.ExportExecution;
import io.github.chrisruffalo.syndo.executions.impl.NoCommandExecution;
import io.github.chrisruffalo.syndo.executions.impl.VersionExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public abstract ExecutionResult execute();

    protected Logger logger() {
        return this.logger;
    }

    public static Execution get(Command command) {
        String parsed = command.getParsedCommand();
        if (parsed == null || parsed.isEmpty()) {
            parsed = "";
        }

        // create the appropriate execution based on the command
        Execution exe;
        switch (parsed.toLowerCase()) {
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
            default:
                exe = new NoCommandExecution(command);
        }

        return exe;
    }

}
