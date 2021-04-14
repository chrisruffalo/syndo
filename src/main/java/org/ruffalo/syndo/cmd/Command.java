package org.ruffalo.syndo.cmd;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

/**
 * This is the root with all the commands that also handles the parse and returns
 * the different command string and options.
 */
public class Command {

    @Parameter(names={"--help", "-h"}, description = "Print this help message", help = true)
    private boolean help = false;

    private CommandBuild build = new CommandBuild();

    private CommandExport export = new CommandExport();

    private CommandBootstrap bootstrap = new CommandBootstrap();

    private String parsedCommand;

    private JCommander commander;

    public CommandBuild getBuild() {
        return build;
    }

    public void setBuild(CommandBuild build) {
        this.build = build;
    }

    public CommandBootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(CommandBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public CommandExport getExport() {
        return export;
    }

    public void setExport(CommandExport export) {
        this.export = export;
    }

    public String getParsedCommand() {
        return parsedCommand;
    }

    public void setParsedCommand(String parsedCommand) {
        this.parsedCommand = parsedCommand;
    }

    public JCommander getCommander() {
        return commander;
    }

    public void setCommander(JCommander commander) {
        this.commander = commander;
    }

    public static Command parse(final String[] args) {
        final Command command = new Command();

        final JCommander commander = JCommander.newBuilder()
                .addObject(command)
                .addCommand("build", command.getBuild())
                .addCommand("bootstrap", command.getBootstrap())
                .addCommand("export", command.getExport())
                .programName("syndo")
                .build();

        commander.parse(args);

        final String commandString = commander.getParsedCommand();
        command.setParsedCommand(commandString);
        command.setCommander(commander);

        return command;
    }

}
