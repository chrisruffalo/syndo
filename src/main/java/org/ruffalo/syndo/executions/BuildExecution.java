package org.ruffalo.syndo.executions;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.build.BuildResult;
import org.ruffalo.syndo.build.BuilderAction;
import org.ruffalo.syndo.build.ComponentBuildAction;
import org.ruffalo.syndo.build.SyndoBuiderAction;
import org.ruffalo.syndo.config.Loader;
import org.ruffalo.syndo.config.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * An execution object is created and configured for an execution. It needs the details necessary to build
 * an OpenShift client and the location of the build yaml file.
 */
public class BuildExecution extends Execution {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final Path pathToConfig;

    private final List<Path> openshiftConfigSearchPaths = new LinkedList<>();

    public BuildExecution(final Path pathToConfig, Path... openshiftConfigSearchPaths) {
        this(pathToConfig, Arrays.asList(openshiftConfigSearchPaths));
    }

    public BuildExecution(final Path pathToConfig, List<Path> openshiftConfigSearchPaths) {
        this.pathToConfig = pathToConfig;
        if (openshiftConfigSearchPaths != null && !openshiftConfigSearchPaths.isEmpty()) {
            this.openshiftConfigSearchPaths.addAll(openshiftConfigSearchPaths);
        }
    }

    public ExecutionResult execute() {
        if (!Files.exists(this.pathToConfig)) {
            logger.error("No build yaml could be found at path: {}", this.pathToConfig);
            return new ExecutionResult(1);
        }

        final ExecutionResult result = new ExecutionResult();

        // load syndo root configuration
        final Root config = Loader.read(pathToConfig);

        // create openshift client
        Config openshiftClientConfig = null;
        if (!this.openshiftConfigSearchPaths.isEmpty()) {
            for (final Path path : this.openshiftConfigSearchPaths) {
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
            final SyndoBuiderAction action1 = new SyndoBuiderAction(BuilderAction.SYNDO, null);
            final BuildResult r1 = action1.build(client);
            if (r1.getStatus().equals(BuildResult.Status.FAILED)) {
                result.setExitCode(1);
                return result;
            }

            final ComponentBuildAction action2 = new ComponentBuildAction(BuilderAction.SYNDO, Paths.get("./sample-build"));
            final BuildResult r2 = action2.build(client);
            if (r2.getStatus().equals(BuildResult.Status.FAILED)) {
                result.setExitCode(1);
                return result;
            }
        }

        return result;
    }

}
