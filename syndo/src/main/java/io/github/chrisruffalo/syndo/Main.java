package io.github.chrisruffalo.syndo;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.executions.Execution;
import io.github.chrisruffalo.syndo.executions.ExecutionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        // parse command
        final Command cmd = Command.parse(args);

        // handle help if asked for
        if (cmd.isHelp()) {
            cmd.getCommander().usage();
            System.exit(0);
        }

        // get execution from execution factory
        final Execution execution = Execution.get(cmd);

        // execute command
        final ExecutionResult result = execution.execute();
        if (result.getExitCode() != 0) {
            System.exit(result.getExitCode());
        }
    }

}
