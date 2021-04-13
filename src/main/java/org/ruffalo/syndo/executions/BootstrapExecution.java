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
import org.ruffalo.syndo.build.SyndoBuiderAction;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.cmd.CommandBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

public class BootstrapExecution extends Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Command command;
    private CommandBootstrap bootstrap;

    public BootstrapExecution(Command command) {
        this.command = command;
        this.bootstrap = command.getBootstrap();
    }

    @Override
    public ExecutionResult execute() {
        final ExecutionResult result = new ExecutionResult();
        final BuildContext context = new BuildContext();
        context.setCommand(command);

        // create openshift client
        final List<Path> openshiftConfigSearchPaths = this.bootstrap.getOpenshiftConfigSearchPaths();
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
            String tmpNamespace = this.bootstrap.getNamespace();
            if (tmpNamespace == null || tmpNamespace.isEmpty()) {
                tmpNamespace = client.getNamespace();
            }
            final String namespace = tmpNamespace;
            context.setNamespace(namespace);

            // todo: warn about 'dangerous' namespaces (default, etc)

            // create or use existing namespace
            if (!this.bootstrap.isDryRun() && client.projects().withName(namespace).get() == null) {
                client.projects().createOrReplace(new ProjectBuilder().withMetadata(new ObjectMetaBuilder().withName(namespace).build()).build());
                logger.debug("Created namespace: {}", namespace);
            }
            logger.info("Namespace: {}", namespace);

            final List<Action> actions = new LinkedList<>();

            final SyndoBuiderAction syndoBuildAction = new SyndoBuiderAction();
            actions.add(syndoBuildAction);

            // execute actions and break on failed action
            for (final Action action : actions) {
                action.build(context);
                if (!BuildContext.Status.OK.equals(context.getStatus())) {
                    if (BuildContext.Status.DONE.equals(context.getStatus())) {
                        logger.info("Build finished");
                    } else {
                        logger.error("Build finished with errors");
                        result.setExitCode(1);
                    }
                    break;
                }
            }

       } catch (KubernetesClientException kce) {
            logger.error("Error connecting to kubernetes: {}", kce.getMessage());
        }

        return result;
    }
}
