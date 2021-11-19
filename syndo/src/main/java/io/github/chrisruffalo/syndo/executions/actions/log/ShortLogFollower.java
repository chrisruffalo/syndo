package io.github.chrisruffalo.syndo.executions.actions.log;

import io.fabric8.kubernetes.client.dsl.LogWatch;

public class ShortLogFollower extends FullLogFollower {

    public ShortLogFollower(LogWatch logWatch) {
        super(logWatch);
    }

    @Override
    protected void processLine(String line) {
        // only log if it starts with the log prefix
        if (line.startsWith(LOG_PREFIX) || line.startsWith(ERROR_PREFIX)) {
            super.processLine(line);
        }
    }

    @Override
    protected void processException(Exception ex) {
        super.processException(ex);
    }
}
