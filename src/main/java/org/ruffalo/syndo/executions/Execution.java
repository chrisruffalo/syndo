package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.cmd.Command;

public abstract class Execution {

    public abstract ExecutionResult execute();

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
