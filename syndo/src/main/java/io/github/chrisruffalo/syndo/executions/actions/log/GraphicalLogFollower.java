package io.github.chrisruffalo.syndo.executions.actions.log;

import io.fabric8.kubernetes.client.dsl.LogWatch;

public class GraphicalLogFollower extends ShortLogFollower {

    public GraphicalLogFollower(LogWatch logWatch) {
        super(logWatch);
    }

    @Override
    protected void processLine(String line) {

    }

    @Override
    protected void processException(Exception ex) {

    }
}
