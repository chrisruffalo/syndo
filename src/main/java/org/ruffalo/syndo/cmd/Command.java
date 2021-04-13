package org.ruffalo.syndo.cmd;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.ruffalo.syndo.cmd.converters.StringToPathListConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * This is the root with all the commands that also handles the parse and returns
 * the different command string and options.
 */
public class Command {

    @Parameter(names={"--help", "-h"}, description = "Print this help message", help = true)
    private boolean help = false;

    @Parameter(names={"--openshift-config-path", "-O"}, description = "Path to the directory where the kube authentication/configuration file can be found", listConverter = StringToPathListConverter.class)
    private List<Path> openshiftConfigSearchPaths = getDefaultSearchPaths();

    private CommandBuild build = new CommandBuild();

    private CommandExport export = new CommandExport();

    private String parsedCommand;

    private JCommander commander;

    public CommandBuild getBuild() {
        return build;
    }

    public void setBuild(CommandBuild build) {
        this.build = build;
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

    public List<Path> getOpenshiftConfigSearchPaths() {
        return openshiftConfigSearchPaths;
    }

    public void setOpenshiftConfigSearchPaths(List<Path> openshiftConfigSearchPaths) {
        this.openshiftConfigSearchPaths = openshiftConfigSearchPaths;
    }

    public static Command parse(final String[] args) {
        final Command command = new Command();

        final JCommander commander = JCommander.newBuilder()
                .addObject(command)
                .addCommand("build", command.getBuild())
                .addCommand("export", command.getExport())
                .programName("syndo")
                .build();

        commander.parse(args);

        final String commandString = commander.getParsedCommand();
        command.setParsedCommand(commandString);
        command.setCommander(commander);

        return command;
    }

    private static List<Path> getDefaultSearchPaths() {
        final List<Path> searchPaths = new LinkedList<>();

        // get root home
        Path home = Paths.get(System.getProperty("user.home"));

        // make sure it exists
        Path homeKube = home.resolve(".kube");
        if (Files.exists(homeKube)) {
            searchPaths.add(homeKube); // and add default place
        }

        // check cygwin places
        Path homeHome = Paths.get(System.getenv("HOME"));
        if (!homeHome.equals(home)) {
            homeKube = homeHome.resolve(".kube");
            if (Files.exists(homeKube)) {
                searchPaths.add(homeKube);
            }
        }

        // check app home on windows
        final String userProfileEnv = System.getenv("USERPROFILE");
        if (userProfileEnv != null && !userProfileEnv.isEmpty()) {
            // get userprofile directory
            Path profileHome = Paths.get(userProfileEnv);
            if (!profileHome.equals(home) && Files.exists(profileHome)) {
                homeKube = profileHome.resolve(".kube");
                if (Files.exists(homeKube)) {
                    searchPaths.add(homeKube);
                }
                homeKube = profileHome.resolve("Documents").resolve(".kube");
                if (Files.exists(homeKube)) {
                    searchPaths.add(homeKube);
                }
            }
        }

        return searchPaths;
    }

}
