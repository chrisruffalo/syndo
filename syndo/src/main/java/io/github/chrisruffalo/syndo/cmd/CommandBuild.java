package io.github.chrisruffalo.syndo.cmd;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.github.chrisruffalo.syndo.cmd.converters.StringToPathConverter;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

@Parameters
public class CommandBuild extends CommandBootstrap {

    @Parameter(description = "Path to the YAML build file that describes the build", required = true, converter = StringToPathConverter.class)
    private Path buildFile;

    @Parameter(names = {"--force", "-f"}, description = "Force the build of all components even if there is an image stream content match")
    private boolean force;

    @Parameter(names={"--components", "-c"}, description = "List of components or aliases to build")
    private List<String> components = new LinkedList<String>(){{
        add("all");
    }};

    @Parameter(names={"--tar", "-t"}, description = "File system path to output build tar to, deletes any existing tar at that path. By default the tar is built in memory")
    private Path tarOutput;

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

    public Path getTarOutput() {
        return tarOutput;
    }

    public void setTarOutput(Path tarOutput) {
        this.tarOutput = tarOutput;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }
}
