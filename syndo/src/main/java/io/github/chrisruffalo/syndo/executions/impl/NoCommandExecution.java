package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.executions.ExecutionResult;
import io.github.chrisruffalo.syndo.executions.Execution;

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
