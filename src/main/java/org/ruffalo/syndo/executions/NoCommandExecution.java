package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.cmd.Command;

public class NoCommandExecution extends Execution{

    private Command command;

    public NoCommandExecution(final Command command) {
        this.command = command;
    }

    @Override
    public ExecutionResult execute() {
        System.err.println("[ERROR] No command given, must invoke a valid command\n");
        this.command.getCommander().usage();
        return new ExecutionResult(1);
    }
}
