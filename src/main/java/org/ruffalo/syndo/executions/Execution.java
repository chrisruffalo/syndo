package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.cmd.Command;

public abstract class Execution {

    public abstract ExecutionResult execute();

    public static Execution get(Command command) {



        // this is the case where no command was selected
        return new NoCommandExecution(command);
    }

}
