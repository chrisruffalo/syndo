package org.ruffalo.syndo.executions.actions.impl;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
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
import org.ruffalo.syndo.cmd.CommandBootstrap;
import org.ruffalo.syndo.exceptions.RuntimeSyndoException;
import org.ruffalo.syndo.executions.actions.BuildContext;
import org.ruffalo.syndo.executions.actions.BuilderAction;
import org.ruffalo.syndo.resources.Resources;
import org.ruffalo.syndo.resources.TarCreator;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class SyndoBuilderAction extends BuilderAction {

    public static final String BOOTSTRAP_RESOURCE_PATH = "containers/syndo-builder";
    public static final String SYNDO_BUILDER = SYNDO + "-builder";

    private static final Path SYNDO_RESOURCE_PATH = bootstrapSyndoResorucePath();

    /**
     * Static method that can be initialized at build time on graal native so that the
     * resources can be loaded.
     *
     * @return the path to the in-memory file system in use for holding resources
     */
    private static Path bootstrapSyndoResorucePath() {
        URL resourceUrl = null;
        try {
            resourceUrl = Thread.currentThread().getContextClassLoader().getResource(BOOTSTRAP_RESOURCE_PATH);
            if (resourceUrl == null) {
                throw new RuntimeException("Cannot load missing " + BOOTSTRAP_RESOURCE_PATH);
            }
            final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
            final Path resourceRoot = fs.getPath("/");
            Resources.exportResourceDir(resourceUrl, resourceRoot);
            return resourceRoot;
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeSyndoException("Could not load internal resource path (" + resourceUrl + ")", e);
        }
    }

    public SyndoBuilderAction() {
    }

    @Override
    public void build(BuildContext context) {
        // get client from context
        final OpenShiftClient client = context.getClient();

        final CommandBootstrap commandBootstrap = context.getCommandBuild() != null ? context.getCommandBuild() : context.getCommand().getBootstrap();

        Path bootstrapDirectory = commandBootstrap.getBootstrapRoot();
        if (bootstrapDirectory == null || !Files.exists(bootstrapDirectory)) {
            bootstrapDirectory = SYNDO_RESOURCE_PATH;
        } else {
            bootstrapDirectory = bootstrapDirectory.normalize().toAbsolutePath();
        }

        final String namespaceName = context.getNamespace();

        String bootstrapHash = "";
        try {
            bootstrapHash = Resources.hashPath(bootstrapDirectory);
        } catch (IOException e) {
            logger().error("Could not compute hash for {}: {}", bootstrapDirectory, e.getMessage());
        }
        final String tag = bootstrapHash.isEmpty() ? "latest" : bootstrapHash;

        String syndoImageName = SYNDO_BUILDER;
        if (!"latest".equalsIgnoreCase(tag)) {
            syndoImageName = String.format("%s-%s", SYNDO_BUILDER, tag);
        }
        context.setBuilderImageName(syndoImageName);

        ImageStreamTag ist = client.imageStreamTags().inNamespace(namespaceName).withName(syndoImageName + ":latest").get();
        if ("latest".equalsIgnoreCase(tag) || ist == null || commandBootstrap.isForceBootstrap()) {
            logger().info("Building syndo-builder from: {} -> {}", bootstrapDirectory, syndoImageName);

            // ensure that the target image stream exists
            final ObjectMeta isMeta = new ObjectMetaBuilder().withName(syndoImageName).build();
            ImageStream is = new ImageStreamBuilder()
                    .withMetadata(isMeta)
                    .build();
            is = client.imageStreams().inNamespace(namespaceName).createOrReplace(is);

            // get dockerfile resource
            final Path bootstrapDockerFile = bootstrapDirectory.resolve("Dockerfile");
            if (!Files.exists(bootstrapDockerFile)) {
                logger().error("Dockerfile not found at {}", bootstrapDockerFile);
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }

            final InputStream stream;
            try {
                stream = Files.newInputStream(bootstrapDockerFile);
            } catch (IOException e) {
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            final String dockerFileContentsString;
            try {
                dockerFileContentsString = new String(IOUtils.toByteArray(stream)).replaceAll("\\r\\n?", "\n");
            } catch (IOException e) {
                logger().error("Could not read embedded dockerfile for syndo-builder: {}", e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }

            // create syndo build configuration
            final ObjectMeta bcMeta = new ObjectMetaBuilder().withName(SYNDO_BUILDER).build();
            final DockerBuildStrategy dockerBuildStrategy = new DockerBuildStrategyBuilder().build();
            final BuildConfigSpec dockerBuildConfigSpec = new BuildConfigSpecBuilder()
                    .withNewStrategy()
                    .withDockerStrategy(dockerBuildStrategy)
                    .endStrategy()
                    .withSource(new BuildSourceBuilder().withDockerfile(dockerFileContentsString).build())
                    .withOutput(new BuildOutputBuilder()
                            .withTo(new ObjectReferenceBuilder().withNamespace(namespaceName).withName(syndoImageName)
                            .build()
                        ).build())
                    .build();
            BuildConfig syndoBuilderConfig = new BuildConfigBuilder()
                    .withSpec(dockerBuildConfigSpec)
                    .withMetadata(bcMeta)
                    .build();
            syndoBuilderConfig = client.buildConfigs().inNamespace(namespaceName).createOrReplace(syndoBuilderConfig);

            // create in-memory/jimfs tar file as target for build-contents tar
            Path tarFile = commandBootstrap.getBootstrapTarOutput();
            if (tarFile == null) {
                tarFile = this.fs().getPath("/", syndoBuilderConfig.getMetadata().getName() + ".tar").normalize().toAbsolutePath();
            }
            try {
                TarCreator.createDirectoryTar(tarFile, bootstrapDirectory);
            } catch (IOException e) {
                logger().error("Could not create tar resource: {}", e.getMessage());
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
                logger().error("Could not start build: {}", ex.getMessage());
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
                bootstrapSucceeded = waitAndWatchBuild(namespaceName, client, build, logger());
            } catch (Exception e) {
                logger().error("Could not wait for build to complete: {}", e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            if (!bootstrapSucceeded) {
                logger().error("Could not build the syndo build container, cannot proceed");
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
        } else {
            logger().info("Found image stream tag matching {} content: {}", SYNDO_BUILDER, ist.getMetadata().getName());
        }

    }
}
