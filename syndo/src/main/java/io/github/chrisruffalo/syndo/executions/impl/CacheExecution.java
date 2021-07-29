package io.github.chrisruffalo.syndo.executions.impl;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.cmd.CommandCache;
import io.github.chrisruffalo.syndo.exceptions.SyndoException;
import io.github.chrisruffalo.syndo.executions.OpenShiftExecution;
import io.github.chrisruffalo.syndo.executions.actions.Action;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.impl.CacheAugmentationServiceAction;

import java.util.LinkedList;
import java.util.List;

public class CacheExecution extends OpenShiftExecution {

    private final Command command;
    private final CommandCache commandCache;

    public CacheExecution(Command command) {
        this.command = command;
        this.commandCache = command.getCache();
    }

    @Override
    protected List<Action> getBuildActions(BuildContext context) {
        final List<Action> actions = new LinkedList<>();
        actions.add(new CacheAugmentationServiceAction());
        return actions;
    }

    @Override
    public CommandOpenShift getOpenShiftCommand() {
        return this.commandCache;
    }

    @Override
    public BuildContext createContext() throws SyndoException {
        final BuildContext context = new BuildContext();
        context.setCommand(command);
        return context;
    }
}
