package org.ruffalo.syndo.executions;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.build.Action;
import org.ruffalo.syndo.build.BuildContext;
import org.ruffalo.syndo.build.BuildPrepareAction;
import org.ruffalo.syndo.build.BuildResolveAction;
import org.ruffalo.syndo.build.ComponentBuildAction;
import org.ruffalo.syndo.build.CreateTarAction;
import org.ruffalo.syndo.build.SyndoBuiderAction;
import org.ruffalo.syndo.build.VerifyAction;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.cmd.CommandBuild;
import org.ruffalo.syndo.config.Loader;
import org.ruffalo.syndo.config.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * An execution object is created and configured for an execution. It needs the details necessary to build
 * an OpenShift client and the location of the build yaml file.
 */
public class BuildExecution extends Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Command command;

    public BuildExecution(final Command command) {
        this.command = command;
    }

    public ExecutionResult execute() {
        final CommandBuild buildCommand = this.command.getBuild();

        final BuildContext context = new BuildContext();
        context.setCommand(command);
        context.setCommandBuild(buildCommand);

        final Path pathToConfig = buildCommand.getBuildFile();
        if (!Files.exists(pathToConfig)) {
            logger.error("No build yaml could be found at path: {}", pathToConfig);
            return new ExecutionResult(1);
        }
        context.setConfigPath(pathToConfig);

        final ExecutionResult result = new ExecutionResult();

        // create openshift client
        final List<Path> openshiftConfigSearchPaths = this.command.getOpenshiftConfigSearchPaths();
        Config openshiftClientConfig = null;
        if (!openshiftConfigSearchPaths.isEmpty()) {
            for (final Path path : openshiftConfigSearchPaths) {
                // start with root config path
                Path configPath = path.resolve("config").normalize().toAbsolutePath();

                // if the file does not exist search for kubeconfig
                if (!Files.exists(configPath)) {
                    configPath = path.resolve("kubeconfig").normalize().toAbsolutePath();
                    // if kubeconfig does not exist move on
                    if (!Files.exists(configPath)) {
                        continue;
                    }
                }

                // otherwise create config client
                try {
                    openshiftClientConfig = Config.fromKubeconfig(null, new String(Files.readAllBytes(configPath)), configPath.toString());
                    logger.info("Using kube configuration: {}", configPath);
                    break;
                } catch (IOException e) {
                    // skip
                }
            }
        }
        // if no configuration found use default
        if (openshiftClientConfig == null) {
            openshiftClientConfig = new ConfigBuilder().build();
            logger.info("Using default kube configuration");
        }

        // load syndo root configuration
        logger.info("Using build file: {}", pathToConfig);
        final Root config = Loader.read(pathToConfig);
        context.setConfig(config);

        // now we can start a build...
        try (
            final KubernetesClient k8s = new DefaultOpenShiftClient(openshiftClientConfig);
            final OpenShiftClient client = k8s.adapt(OpenShiftClient.class)
       ) {
            // set client on context
            context.setClient(client);

            // check kubernetes client
            final URL ocUrl = client.getOpenshiftUrl();
            if (ocUrl == null) {
                logger.error("No OpenShift url available");
                result.setExitCode(1);
                return result;
            }
            logger.info("OpenShift: {}", ocUrl);

            // get namespace
            String tmpNamespace = buildCommand.getNamespace();
            if (tmpNamespace == null || tmpNamespace.isEmpty()) {
                tmpNamespace = client.getNamespace();
            }
            final String namespace = tmpNamespace;
            context.setNamespace(namespace);

            // todo: warn about 'dangerous' namespaces (default, etc)

            // create or use existing namespace
            if (!buildCommand.isDryRun() && client.projects().withName(namespace).get() == null) {
                client.projects().createOrReplace(new ProjectBuilder().withMetadata(new ObjectMetaBuilder().withName(namespace).build()).build());
                logger.debug("Created namespace: {}", namespace);
            }
            logger.info("Namespace: {}", namespace);

            final List<Action> actions = new LinkedList<>();

            // verify configuration
            final VerifyAction verifyAction = new VerifyAction();
            actions.add(verifyAction);

            final SyndoBuiderAction syndoBuildAction = new SyndoBuiderAction();
            actions.add(syndoBuildAction);

            // prepare build
            final BuildPrepareAction prepareAction = new BuildPrepareAction();
            actions.add(prepareAction);

            // resolve artifacts inputs/outputs and build order from dependencies
            final BuildResolveAction resolveAction = new BuildResolveAction();
            actions.add(resolveAction);

            // create build tar
            final CreateTarAction createTarAction = new CreateTarAction();
            actions.add(createTarAction);

            final ComponentBuildAction componentBuildAction = new ComponentBuildAction();
            actions.add(componentBuildAction);

            // execute actions and break on failed action
            for (final Action action : actions) {
                action.build(context);
                if (!BuildContext.Status.OK.equals(context.getStatus())) {
                    result.setExitCode(1);
                    break;
                }
            }

        } catch (KubernetesClientException kce) {
            logger.error("Error connecting to kubernetes: {}", kce.getMessage());
        }

        return result;
    }

}
