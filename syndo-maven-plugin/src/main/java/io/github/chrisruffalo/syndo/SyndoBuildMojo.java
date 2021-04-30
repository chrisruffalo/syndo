package io.github.chrisruffalo.syndo;

import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandBuild;
import io.github.chrisruffalo.syndo.executions.Execution;
import io.github.chrisruffalo.syndo.executions.ExecutionResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;

@Mojo(name = "build")
public class SyndoBuildMojo extends AbstractMojo {

    /**
     * Path to the directory where the OC command is.
     */
    @Parameter(property = "syndo.kubePath")
    private File kubeConfigPath;

    /*
     * Name of the OpenShift project/namespace to build the Syndo
     * artifacts into and to use as the target of the build process.
     */
    @Parameter(property = "syndo.namespace")
    private String namespace;

    /**
     * The configuration file used for the build. A build
     * file is required to execute a syndo build.
     */
    @Parameter(required = true, property = "syndo.config")
    private File buildFile;

    /**
     * If true forces rebuild, defaults to false.
     */
    @Parameter(property = "syndo.force")
    private boolean force = false;

    /**
     * The directory to use for bootstrapping the
     * syndo content (has the Dockerfile and other
     * build content)
     */
    @Parameter(property = "syndo.bootstrap.dir")
    private File bootstrapDir;

    /**
     * True if the bootstrap needs to be forced. The
     * bootstrap will otherwise only be performed if
     * the contents of the bootstrap directory have
     * not been used to create a bootstrap image already.
     */
    @Parameter(defaultValue = "false", property = "syndo.bootstrap.force")
    private boolean forceBootstrap = false;

    /**
     * An optional comma-separated list of components. Defaults to "all".
     */
    @Parameter(defaultValue = "all", property = "syndo.components")
    private String components;

    /**
     * If true causes Syndo to skip SSL verification with the OpenShift client
     */
    @Parameter(defaultValue = "false", property = "syndo.ssl.skip-verification")
    private boolean skipSslVerification;

    /**
     * Properties passed in to the plugin to use in resolution of the build
     * yaml file.
     */
    @Parameter
    private Map<String, String> properties;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (this.buildFile == null) {
            throw new MojoFailureException("Could not execute syndo build, no build file given");
        }

        if (!this.buildFile.exists()) {
            throw new MojoFailureException(String.format("Build file '%s' does not exist", this.buildFile));
        }

        // build the root command that will be used to construct the execution
        final Command command = new Command();

        // using properties create a build context and then load the build context
        // as though it was being executed from the command line
        final CommandBuild build = new CommandBuild();
        command.setBuild(build);

        // add kube path
        if(this.kubeConfigPath != null) {
            if (!this.kubeConfigPath.exists()) {
                throw new MojoExecutionException(String.format("The kube config path %s is specified but does not exist", this.kubeConfigPath));
            }
            // insert kube path to start of search path for kube file
            build.getOpenshiftConfigSearchPaths().add(0, this.kubeConfigPath.toPath());
        }

        // set namespace
        if (this.namespace != null && !this.namespace.isEmpty()) {
            build.setNamespace(namespace);
        }

        // set build file and if we should force the build
        build.setBuildFile(this.buildFile.toPath());
        build.setForce(this.force);
        build.setSslSkipVerify(this.skipSslVerification);

        // if a bootstrap directory is specified use it
        if (this.bootstrapDir != null) {
            if (!this.bootstrapDir.exists()) {
                throw new MojoExecutionException(String.format("Bootstrap directory %s specified but does not exist", this.bootstrapDir));
            }
            build.setBootstrapRoot(this.bootstrapDir.toPath());
        }
        build.setForceBootstrap(this.forceBootstrap);

        // handle components
        if (this.components == null || this.components.isEmpty()) {
            build.setComponents(Collections.emptyList());
        } else if(this.components.contains(",")) {
            if (build.getComponents() == null) {
                build.setComponents(new LinkedList<>());
            } else {
                build.getComponents().clear();
            }
            final String[] componentArray = this.components.split(",");
            Arrays.stream(componentArray).filter(Objects::nonNull).forEach(component -> {
                component = component.trim();
                if (!component.isEmpty()) {
                    build.getComponents().add(component);
                }
            });
        } else {
            build.setComponents(Collections.singletonList(this.components));
        }

        // add properties map if given
        if(this.properties != null && !this.properties.isEmpty()) {
            command.setProperties(properties);
        }

        // create execution
        command.setParsedCommand("build");
        final Execution execution = Execution.get(command);

        final Logger logger = LoggerFactory.getLogger(this.getClass());
        if (!System.getenv().isEmpty()) {
            logger.debug("Environment:");
            System.getenv().forEach((key, value) -> {
                logger.debug("{} = {}", key, value);
            });
        }

        if (!System.getProperties().isEmpty()) {
            logger.debug("Effective properties:");
            // log system properties
            System.getProperties().forEach((key, value) -> {
                logger.debug("{} = {}", key, value);
            });
        }

        // execute and collect result, throwing an exception on a bad result
        final ExecutionResult result = execution.execute();
        if (result.getExitCode() != 0) {
            if (result.getThrowable() != null) {
                throw new MojoFailureException(result.getMessage(), result.getThrowable());
            }
            throw new MojoFailureException(result.getMessage());
        }
    }
}
