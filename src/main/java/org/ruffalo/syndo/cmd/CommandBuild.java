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

    @Parameter(names={"--components", "-c"}, description = "List of components or aliases to build")
    private List<String> components = new LinkedList<String>(){{
        add("all");
    }};


}
