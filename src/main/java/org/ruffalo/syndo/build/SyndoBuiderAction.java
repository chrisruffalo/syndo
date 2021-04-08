package org.ruffalo.syndo.build;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.compress.utils.IOUtils;
import org.ruffalo.syndo.resources.SyndoTarCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class SyndoBuiderAction extends BuilderAction {

    private static final Logger logger = LoggerFactory.getLogger(SyndoBuiderAction.class);

    public static final String SYNDO_BUILDER = SYNDO + "-builder";
    public static final String SYNDO_BUILDER_LATEST = SYNDO_BUILDER + ":latest";

    private final String targetNamespace;

    public SyndoBuiderAction(final String targetNamespace) {
        this.targetNamespace = targetNamespace;
    }

    @Override
    public BuildResult build(OpenShiftClient client) {
        final BuildResult result = new BuildResult();

        Namespace namespace = client.namespaces().withName(this.targetNamespace).get();
        if (namespace == null) {
            final ObjectMeta metadata = new ObjectMeta();
            metadata.setName(this.targetNamespace);
            namespace = client.namespaces().create(new NamespaceBuilder().withMetadata(metadata).build());
        }
        final String namespaceName = namespace.getMetadata().getName();

        final ImageStreamTag ist = client.imageStreamTags().inNamespace(namespaceName).withName(SYNDO_BUILDER_LATEST).get();
        if (ist == null) {
            // ensure that the target image stream exists
            final ObjectMeta isMeta = new ObjectMetaBuilder().withName(SYNDO_BUILDER).build();
            ImageStream is = new ImageStreamBuilder()
                    .withMetadata(isMeta)
                    .build();
            is = client.imageStreams().inNamespace(namespaceName).createOrReplace(is);

            // get dockerfile resource
            final InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("containers/syndo-builder/Dockerfile");
            if (stream == null) {
                logger.error("Could not find embedded dockerfile for syndo-builder");
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }
            final String dockerFileString;
            try {
                dockerFileString = new String(IOUtils.toByteArray(stream));
            } catch (IOException e) {
                logger.error("Could not read embedded dockerfile for syndo-builder: {}", e.getMessage());
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }

            // create syndo build configuration
            final ObjectMeta bcMeta = new ObjectMetaBuilder().withName(SYNDO_BUILDER).addToLabels(CREATED_FOR, SYNDO_BUILDER).build();
            final DockerBuildStrategy dockerBuildStrategy = new DockerBuildStrategyBuilder().build();
            final BuildConfigSpec dockerBuildConfigSpec = new BuildConfigSpecBuilder()
                    .withNewStrategy()
                    .withDockerStrategy(dockerBuildStrategy)
                    .endStrategy()
                    .withSource(new BuildSourceBuilder().withDockerfile(dockerFileString).build())
                    .withOutput(new BuildOutputBuilder().withNewTo().withNamespace(namespaceName).withName(is.getMetadata().getName()).endTo().build())
                    .build();
            BuildConfig syndoBuilderConfig = new BuildConfigBuilder()
                    .withSpec(dockerBuildConfigSpec)
                    .withMetadata(bcMeta)
                    .build();
            syndoBuilderConfig = client.buildConfigs().inNamespace(namespaceName).createOrReplace(syndoBuilderConfig);

            URL url = Thread.currentThread().getContextClassLoader().getResource("containers/syndo-builder");
            if (url == null) {
                logger.error("Could not load embedded resource folder.");
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }
            final Path tarFile = this.fs().getPath("/", syndoBuilderConfig.getMetadata().getName() + ".tar").normalize().toAbsolutePath();
            try {
                SyndoTarCreator.createResourceTar(tarFile, url);
            } catch (URISyntaxException | IOException e) {
                logger.error("Could not create tar resource: {}", e.getMessage());
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }

            // create build from uploaded tar file
            Build build = null;
            try {
                build = client.buildConfigs()
                        .inNamespace(namespaceName)
                        .withName(syndoBuilderConfig.getMetadata().getName())
                        .instantiateBinary().fromInputStream(Files.newInputStream(tarFile));
            } catch (KubernetesClientException | IOException ex) {
                logger.error("Could not start build: {}", ex.getMessage());
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }

            boolean bootstrapSucceeded;
            try {
                bootstrapSucceeded = waitAndWatchBuild(namespaceName, client, build, logger);
            } catch (Exception e) {
                logger.error("Could not wait for build to complete: {}", e.getMessage());
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }
            if (!bootstrapSucceeded) {
                logger.error("Could not build the syndo build container, cannot proceed");
                result.setStatus(BuildResult.Status.FAILED);
                return result;
            }
        }
        return result;
    }
}
