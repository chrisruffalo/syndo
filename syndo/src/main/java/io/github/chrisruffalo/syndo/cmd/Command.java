package io.github.chrisruffalo.syndo.cmd;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.util.HashMap;
import java.util.Map;

/**
 * This is the root with all the commands that also handles the parse and returns
 * the different command string and options.
 */
public class Command {

    /**
     * Triggers the help display
     */
    @Parameter(names={"--help", "-h"}, description = "Print the help message", help = true)
    private boolean help = false;

    /**
     * Properties passed in that will be added to the resolution of the build yaml file
     */
    @DynamicParameter(names = {"--property", "-P"}, description = "Properties that are provided for resolution during Syndo execution")
    private Map<String, String> properties = new HashMap<>();

    private CommandBuild build = new CommandBuild();

    private CommandExport export = new CommandExport();

    private CommandBootstrap bootstrap = new CommandBootstrap();

    private CommandVersion version = new CommandVersion();

    private CommandCache cache = new CommandCache();

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

    public CommandVersion getVersion() {
        return version;
    }

    public void setVersion(CommandVersion version) {
        this.version = version;
    }

    public CommandCache getCache() {
        return cache;
    }

    public void setCache(CommandCache cache) {
        this.cache = cache;
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

    public Map<String, String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, String> properties) {
        this.properties = properties;
    }

    public static Command parse(final String[] args) {
        final Command command = new Command();

        final JCommander commander = JCommander.newBuilder()
                .addObject(command)
                .addCommand("build", command.getBuild())
                .addCommand("bootstrap", command.getBootstrap())
                .addCommand("export", command.getExport())
                .addCommand("version", command.getVersion())
                .addCommand("cache", command.getCache())
                .programName("syndo")
                .build();

        commander.parse(args);

        final String commandString = commander.getParsedCommand();
        command.setParsedCommand(commandString);
        command.setCommander(commander);

        return command;
    }

}
