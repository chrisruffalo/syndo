package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandBootstrap;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.Action;
import io.github.chrisruffalo.syndo.executions.actions.impl.SyndoBuilderAction;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.executions.OpenShiftExecution;

import java.util.Collections;
import java.util.List;

public class BootstrapExecution extends OpenShiftExecution {

    private final Command command;
    private final CommandBootstrap bootstrap;

    public BootstrapExecution(Command command) {
        this.command = command;
        this.bootstrap = command.getBootstrap();
    }

    @Override
    protected List<Action> getBuildActions(BuildContext context) {
        final SyndoBuilderAction syndoBuildAction = new SyndoBuilderAction();
        return Collections.singletonList(syndoBuildAction);
    }

    @Override
    public CommandOpenShift getOpenShiftCommand() {
        return this.bootstrap;
    }

    @Override
    public BuildContext createContext() {
        final BuildContext context = new BuildContext();
        context.setCommand(command);
        return context;
    }

}
