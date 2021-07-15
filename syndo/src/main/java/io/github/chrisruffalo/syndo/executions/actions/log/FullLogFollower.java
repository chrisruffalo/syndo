package io.github.chrisruffalo.syndo.executions.actions.log;

import io.fabric8.kubernetes.client.dsl.LogWatch;

public class FullLogFollower extends LogFollower {

    public FullLogFollower(LogWatch logWatch) {
        super(logWatch);
    }

    @Override
    protected void processLine(String line) {
        if (line.startsWith(LogFollower.LOG_PREFIX)) {
            line = line.substring(LogFollower.LOG_PREFIX.length()).trim();
        }
        // status lines are not reported
        if (line.startsWith(LogFollower.STATUS_PREFIX)) {
            return;
        }
        // error lines go to stdout
        if (line.startsWith(LogFollower.ERROR_PREFIX)) {
            line = line.substring(LogFollower.ERROR_PREFIX.length()).trim();
            System.err.println(line);
            return;
        }

        // print line
        System.out.println(line);
    }

    @Override
    protected void processException(Exception ex) {
        System.err.printf("An error occurred waiting for build logs: %s%n", ex.getMessage());
    }
}
