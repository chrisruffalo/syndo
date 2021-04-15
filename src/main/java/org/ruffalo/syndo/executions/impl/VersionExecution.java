package org.ruffalo.syndo.executions.impl;

import org.ruffalo.syndo.executions.Execution;
import org.ruffalo.syndo.executions.ExecutionResult;
import org.ruffalo.syndo.info.BuildProperties;

public class VersionExecution extends Execution {

    @Override
    public ExecutionResult execute() {
        System.out.println(BuildProperties.getVersion());
        return new ExecutionResult();
    }
}
