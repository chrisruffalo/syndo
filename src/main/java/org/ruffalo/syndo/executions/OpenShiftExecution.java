package org.ruffalo.syndo.executions;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.build.BuildContext;
import org.ruffalo.syndo.cmd.CommandOpenShift;
import org.ruffalo.syndo.exceptions.SyndoException;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class OpenShiftExecution extends ActionExecution {

    public abstract CommandOpenShift getOpenShiftCommand();

    @Override
    public ExecutionResult execute() {
        final BuildContext context;
        try {
            context = createContext();
        } catch (SyndoException e) {
            logger().error(e.getMessage());
            return new ExecutionResult(1);
        }

        final CommandOpenShift commandOpenShift = this.getOpenShiftCommand();

        // create openshift client
        final List<Path> openshiftConfigSearchPaths = commandOpenShift.getOpenshiftConfigSearchPaths();
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
                    logger().info("Using kube configuration: {}", configPath);
                    break;
                } catch (IOException e) {
                    // skip
                }
            }
        }
        // if no configuration found use default
        if (openshiftClientConfig == null) {
            openshiftClientConfig = new ConfigBuilder().build();
            logger().info("Using default kube configuration");
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
                logger().error("No OpenShift url available");
                return new ExecutionResult(1);
            }
            logger().info("OpenShift: {}", ocUrl);

            // get namespace
            String tmpNamespace = commandOpenShift.getNamespace();
            if (tmpNamespace == null || tmpNamespace.isEmpty()) {
                tmpNamespace = client.getNamespace();
            }
            final String namespace = tmpNamespace;
            context.setNamespace(namespace);

            // todo: warn about 'dangerous' namespaces (default, etc)

            // create or use existing namespace
            if (!commandOpenShift.isDryRun() && client.projects().withName(namespace).get() == null) {
                client.projects().createOrReplace(new ProjectBuilder().withMetadata(new ObjectMetaBuilder().withName(namespace).build()).build());
                logger().debug("Created namespace: {}", namespace);
            }
            logger().info("Namespace: {}", namespace);

            // execute build actions
            return executeActions(context);
        } catch (KubernetesClientException kce) {
            logger().error("Error connecting to kubernetes: {}", kce.getMessage());
            return new ExecutionResult(1);
        }
    }
}
