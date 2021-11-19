package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.executions.Execution;
import io.github.chrisruffalo.syndo.executions.ExecutionResult;
import io.github.chrisruffalo.syndo.info.BuildProperties;

public class VersionExecution extends Execution {

    @Override
    public ExecutionResult execute() {
        System.out.println(BuildProperties.getVersion());
        return new ExecutionResult();
    }
}
