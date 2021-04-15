package org.ruffalo.syndo.executions.impl;

import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.executions.Execution;
import org.ruffalo.syndo.executions.ExecutionResult;

public class NoCommandExecution extends Execution {

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
