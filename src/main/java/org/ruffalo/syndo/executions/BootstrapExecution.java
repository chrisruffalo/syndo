package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.build.Action;
import org.ruffalo.syndo.build.BuildContext;
import org.ruffalo.syndo.build.SyndoBuiderAction;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.cmd.CommandBootstrap;
import org.ruffalo.syndo.cmd.CommandOpenShift;

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
        final SyndoBuiderAction syndoBuildAction = new SyndoBuiderAction();
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
