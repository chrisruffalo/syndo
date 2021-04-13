package org.ruffalo.syndo.cmd;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.ruffalo.syndo.cmd.converters.StringToPathConverter;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

@Parameters
public class CommandBuild extends SubCommand {

    @Parameter(names={"--bootstrap-path", "-P"}, description = "Path to the bootstrap artifacts to use for the Syndo builder container image, providing this option forces the build of builder container", converter = StringToPathConverter.class)
    private Path bootstrapRoot;

    @Parameter(names={"--force-bootstrap", "-F"}, description = "Setting this option to true forces the build of the Syndo builder container even if the proper version is already present")
    private boolean forceBootstrap;

    @Parameter(description = "Path to the YAML build file that describes the build", required = true, converter = StringToPathConverter.class)
    private Path buildFile;

    @Parameter(names={"--namespace", "-n"}, description = "Target OpenShift namespace that will be used for the resolution of build artifacts and as the ouput target for build items, defaults to the current namespace in use by the oc client")
    private String namespace;

    @Parameter(names={"--components", "-c"}, description = "List of components or aliases to build")
    private List<String> components = new LinkedList<String>(){{
        add("all");
    }};

    @Parameter(names={"--dry-run", "-D"}, description = "If true a 'dry-run' will create all output artifacts and prepare for a build but will not actually insert resources into OpenShift or initiate any builds")
    private boolean dryRun = false;

    @Parameter(names={"--tar", "-t"}, description = "File system path to output build tar to, deletes any existing tar at that path. By default the tar is built in memory")
    private Path tarOutput;

    public Path getBootstrapRoot() {
        return bootstrapRoot;
    }

    public void setBootstrapRoot(Path bootstrapRoot) {
        this.bootstrapRoot = bootstrapRoot;
    }

    public boolean isForceBootstrap() {
        return forceBootstrap;
    }

    public void setForceBootstrap(boolean forceBootstrap) {
        this.forceBootstrap = forceBootstrap;
    }

    public Path getBuildFile() {
        return buildFile;
    }

    public void setBuildFile(Path buildFile) {
        this.buildFile = buildFile;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Path getTarOutput() {
        return tarOutput;
    }

    public void setTarOutput(Path tarOutput) {
        this.tarOutput = tarOutput;
    }
}
