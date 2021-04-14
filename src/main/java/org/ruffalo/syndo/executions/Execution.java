package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.cmd.Command;
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
            default:
                exe = new NoCommandExecution(command);
        }

        return exe;
    }

}
