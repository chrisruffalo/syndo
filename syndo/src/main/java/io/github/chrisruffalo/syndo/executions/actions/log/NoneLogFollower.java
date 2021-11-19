package io.github.chrisruffalo.syndo.executions.actions.log;

import io.fabric8.kubernetes.client.dsl.LogWatch;

public class NoneLogFollower extends LogFollower {

    public NoneLogFollower(LogWatch logWatch) {
        super(logWatch);
    }

    @Override
    protected void processLine(String line) {
        // no-op
    }

    @Override
    protected void processException(Exception ex) {
        // no-op
    }
}
