package org.ruffalo.syndo.cmd;


import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.ruffalo.syndo.cmd.converters.StringToPathConverter;

import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

@Parameters
public class CommandExport extends SubCommand {

    @Parameter(description = "Path to write the bootstrap environment to. When building this path can provide the bootstrap path with the customized artifacts.", required = true, converter = StringToPathConverter.class)
    private Path bootstrapRoot;

    public Path getBootstrapRoot() {
        return bootstrapRoot;
    }

    public void setBootstrapRoot(Path bootstrapRoot) {
        this.bootstrapRoot = bootstrapRoot;
    }
}
