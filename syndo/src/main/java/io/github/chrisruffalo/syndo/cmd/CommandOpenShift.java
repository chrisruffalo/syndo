package io.github.chrisruffalo.syndo.cmd;

import com.beust.jcommander.Parameter;
import io.github.chrisruffalo.syndo.cmd.converters.StringToPathListConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class CommandOpenShift extends SubCommand {

    @Parameter(names={"--namespace", "-n"}, description = "Target OpenShift namespace that will be used for the resolution of build artifacts and as the ouput target for build items, defaults to the current namespace in use by the oc client")
    private String namespace;

    @Parameter(names={"--openshift-config-path", "-O"}, description = "Path to the directory where the kube authentication/configuration file can be found", listConverter = StringToPathListConverter.class)
    private List<Path> openshiftConfigSearchPaths = getDefaultSearchPaths();

    @Parameter(names={"--dry-run", "-D"}, description = "If true a 'dry-run' will create all output artifacts and prepare for a build but will not actually insert resources into OpenShift or initiate any builds")
    private boolean dryRun = false;

    @Parameter(names = {"--ssl-skip-verify"}, description = "If true then skip SSL verification")
    private boolean sslSkipVerify = false;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public List<Path> getOpenshiftConfigSearchPaths() {
        return openshiftConfigSearchPaths;
    }

    public void setOpenshiftConfigSearchPaths(List<Path> openshiftConfigSearchPaths) {
        this.openshiftConfigSearchPaths = openshiftConfigSearchPaths;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isSslSkipVerify() {
        return sslSkipVerify;
    }

    public void setSslSkipVerify(boolean sslSkipVerify) {
        this.sslSkipVerify = sslSkipVerify;
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

        // check HOME environment variable (allows cygwin or weird setups to try and work)
        final String homeEnv = System.getenv("HOME");
        if (homeEnv != null && !homeEnv.isEmpty()) {
            Path homeHome = Paths.get(homeEnv);
            if (!homeHome.equals(home)) {
                homeKube = homeHome.resolve(".kube");
                if (Files.exists(homeKube)) {
                    searchPaths.add(homeKube);
                }
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
