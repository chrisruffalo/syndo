package io.github.chrisruffalo.syndo.cmd;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import io.github.chrisruffalo.syndo.cmd.converters.StringToPathConverter;

import java.nio.file.Path;

@Parameters(commandDescription = "Bootstrap the syndo builder into the target namespace")
public class CommandBootstrap extends CommandOpenShift {

    @Parameter(names={"--bootstrap-path", "-P"}, description = "Path to the bootstrap artifacts to use for the Syndo builder container image, providing this option forces the build of builder container", converter = StringToPathConverter.class)
    private Path bootstrapRoot;

    @Parameter(names={"--force-bootstrap", "-F"}, description = "Setting this option to true forces the build of the Syndo builder container even if the proper version is already present")
    private boolean forceBootstrap;

    @Parameter(names={"--bootstrap-tar", "-T"}, description = "File system path to output bootstrap tar to, deletes any existing tar at that path. By default the tar is built in memory")
    private Path bootstrapTarOutput;

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

    public Path getBootstrapTarOutput() {
        return bootstrapTarOutput;
    }

    public void setBootstrapTarOutput(Path bootstrapTarOutput) {
        this.bootstrapTarOutput = bootstrapTarOutput;
    }
}
