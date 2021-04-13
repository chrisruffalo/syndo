package org.ruffalo.syndo.build;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import io.fabric8.openshift.api.model.BuildOutputBuilder;
import io.fabric8.openshift.api.model.BuildSourceBuilder;
import io.fabric8.openshift.api.model.DockerBuildStrategy;
import io.fabric8.openshift.api.model.DockerBuildStrategyBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.compress.utils.IOUtils;
import org.ruffalo.syndo.resources.ExportResources;
import org.ruffalo.syndo.resources.SyndoTarCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class SyndoBuiderAction extends BuilderAction {

    private static final Logger logger = LoggerFactory.getLogger(SyndoBuiderAction.class);

    public static final String BOOTSTRAP_RESOURCE_PATH = "containers/syndo-builder";

    public static final String SYNDO_BUILDER = SYNDO + "-builder";
    public static final String SYNDO_BUILDER_LATEST = SYNDO_BUILDER + ":latest";

    public SyndoBuiderAction() {
    }

    @Override
    public void build(BuildContext context) {
        // get client from context
        final OpenShiftClient client = context.getClient();

        boolean forceBuild = false;
        Path bootstrapDirectory = context.getCommandBuild().getBootstrapRoot();
        if (!Files.exists(bootstrapDirectory)) {
            try {
                bootstrapDirectory = ExportResources.resourceToPath(Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResource(BOOTSTRAP_RESOURCE_PATH)));
            } catch (URISyntaxException | IOException e) {
                throw new RuntimeException("Could not load internal resource path (" + BOOTSTRAP_RESOURCE_PATH + ") when bootstrap directory is null or unavailable", e);
            }
        } else {
            forceBuild = true;
            bootstrapDirectory = bootstrapDirectory.normalize().toAbsolutePath();
        }
        forceBuild = forceBuild || context.getCommandBuild().isForceBootstrap();

        final String namespaceName = context.getNamespace();

        final ImageStreamTag ist = client.imageStreamTags().inNamespace(namespaceName).withName(SYNDO_BUILDER_LATEST).get();
        if (ist == null || forceBuild) {

            // ensure that the target image stream exists
            final ObjectMeta isMeta = new ObjectMetaBuilder().withName(SYNDO_BUILDER).build();
            ImageStream is = new ImageStreamBuilder()
                    .withMetadata(isMeta)
                    .build();
            is = client.imageStreams().inNamespace(namespaceName).createOrReplace(is);

            // get dockerfile resource
            final Path bootstrapDockerFile = bootstrapDirectory.resolve("Dockerfile");
            if (!Files.exists(bootstrapDockerFile)) {
                logger.error("Dockerfile not found at {}", bootstrapDockerFile);
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            logger.info("Bootstrap syndo-builder from: {}", bootstrapDirectory);

            final InputStream stream;
            try {
                stream = Files.newInputStream(bootstrapDockerFile);
            } catch (IOException e) {
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            final String dockerFileContentsString;
            try {
                dockerFileContentsString = new String(IOUtils.toByteArray(stream));
            } catch (IOException e) {
                logger.error("Could not read embedded dockerfile for syndo-builder: {}", e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }

            // create syndo build configuration
            final ObjectMeta bcMeta = new ObjectMetaBuilder().withName(SYNDO_BUILDER).addToLabels(CREATED_FOR, SYNDO_BUILDER).build();
            final DockerBuildStrategy dockerBuildStrategy = new DockerBuildStrategyBuilder().build();
            final BuildConfigSpec dockerBuildConfigSpec = new BuildConfigSpecBuilder()
                    .withNewStrategy()
                    .withDockerStrategy(dockerBuildStrategy)
                    .endStrategy()
                    .withSource(new BuildSourceBuilder().withDockerfile(dockerFileContentsString).build())
                    .withOutput(new BuildOutputBuilder().withNewTo().withNamespace(namespaceName).withName(is.getMetadata().getName()).endTo().build())
                    .build();
            BuildConfig syndoBuilderConfig = new BuildConfigBuilder()
                    .withSpec(dockerBuildConfigSpec)
                    .withMetadata(bcMeta)
                    .build();
            syndoBuilderConfig = client.buildConfigs().inNamespace(namespaceName).createOrReplace(syndoBuilderConfig);

            // create in-memory/jimfs tar file as target for build-contents tar
            final Path tarFile = this.fs().getPath("/", syndoBuilderConfig.getMetadata().getName() + ".tar").normalize().toAbsolutePath();
            try {
                SyndoTarCreator.createDirectoryTar(tarFile, bootstrapDirectory);
            } catch (IOException e) {
                logger.error("Could not create tar resource: {}", e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
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
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }

            try {
                Files.deleteIfExists(tarFile);
            } catch (IOException ex) {
                // nothing to do if this fails, just move on
            }

            boolean bootstrapSucceeded;
            try {
                bootstrapSucceeded = waitAndWatchBuild(namespaceName, client, build, logger);
            } catch (Exception e) {
                logger.error("Could not wait for build to complete: {}", e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            if (!bootstrapSucceeded) {
                logger.error("Could not build the syndo build container, cannot proceed");
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
        }

    }
}
