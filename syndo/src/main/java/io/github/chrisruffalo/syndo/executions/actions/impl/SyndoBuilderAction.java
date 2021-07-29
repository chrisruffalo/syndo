package io.github.chrisruffalo.syndo.executions.actions.impl;

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
import io.github.chrisruffalo.syndo.exceptions.RuntimeSyndoException;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.BuilderAction;
import io.github.chrisruffalo.syndo.resources.Resources;
import io.github.chrisruffalo.syndo.resources.TarCreator;
import org.apache.commons.compress.utils.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Build an embedded/bootstrappable image
 */
public abstract class SyndoBuilderAction extends BuilderAction {

    public SyndoBuilderAction() {

    }

    protected abstract Path getResourcePath(final BuildContext context);

    protected abstract String getImageName(final BuildContext context);

    protected abstract String targetNamespace(final BuildContext context);

    protected abstract boolean isForced(final BuildContext context);

    protected abstract Path outputTar(final BuildContext context);

    protected void saveImageName(final BuildContext context, final String imageName) {
        // no-op by default
    }

    /**
     * Static method that can be initialized at build time on graal native so that the
     * resources can be loaded.
     *
     * @return the path to the in-memory file system in use for holding resources
     */
    protected static Path bootstrapSyndoResourcePath(final String resourcePath) {
        URL resourceUrl = null;
        try {
            resourceUrl = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
            if (resourceUrl == null) {
                throw new RuntimeException("Cannot load missing " + resourcePath);
            }
            final FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
            final Path resourceRoot = fs.getPath("/");
            Resources.exportResourceDir(resourceUrl, resourceRoot);
            return resourceRoot;
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeSyndoException("Could not load internal resource path (" + resourceUrl + ")", e);
        }
    }

    @Override
    public void build(BuildContext context) {
        // get client from context
        final OpenShiftClient client = context.getClient();
        final String namespace = this.targetNamespace(context);

        // error out if
        if (namespace == null || namespace.isEmpty()) {
            logger().error("Could not determine target namespace");
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }

        Path bootstrapDirectory = this.getResourcePath(context);

        String bootstrapHash = "";
        try {
            bootstrapHash = Resources.hashPath(bootstrapDirectory);
        } catch (IOException e) {
            logger().error("Could not compute hash for {}: {}", bootstrapDirectory, e.getMessage());
        }
        final String tag = bootstrapHash.isEmpty() ? "latest" : bootstrapHash;

        final String baseImageName = getImageName(context);
        String syndoImageName = baseImageName;
        if (!"latest".equalsIgnoreCase(tag)) {
            syndoImageName = String.format("%s-%s", syndoImageName, tag);
        }
        this.saveImageName(context, syndoImageName);

        ImageStreamTag ist = client.imageStreamTags().inNamespace(namespace).withName(syndoImageName + ":latest").get();
        if ("latest".equalsIgnoreCase(tag) || ist == null || isForced(context)) {
            logger().info("Building {} from: {} -> {}", baseImageName, bootstrapDirectory, syndoImageName);

            // ensure that the target image stream exists
            final ObjectMeta isMeta = new ObjectMetaBuilder().withName(syndoImageName).build();
            ImageStream is = new ImageStreamBuilder()
                    .withMetadata(isMeta)
                    .build();
            is = client.imageStreams().inNamespace(namespace).createOrReplace(is);

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
                logger().error("Could not read embedded dockerfile for {}: {}", baseImageName, e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }

            // create syndo build configuration
            final ObjectMeta bcMeta = new ObjectMetaBuilder().withName(baseImageName).withNamespace(namespace).build();
            final DockerBuildStrategy dockerBuildStrategy = new DockerBuildStrategyBuilder().build();
            final BuildConfigSpec dockerBuildConfigSpec = new BuildConfigSpecBuilder()
                    .withNewStrategy()
                    .withDockerStrategy(dockerBuildStrategy)
                    .endStrategy()
                    .withSource(new BuildSourceBuilder().withDockerfile(dockerFileContentsString).build())
                    .withOutput(new BuildOutputBuilder()
                            .withTo(new ObjectReferenceBuilder().withNamespace(namespace).withName(syndoImageName)
                            .build()
                        ).build())
                    .build();
            BuildConfig syndoBuilderConfig = new BuildConfigBuilder()
                    .withSpec(dockerBuildConfigSpec)
                    .withMetadata(bcMeta)
                    .build();
            syndoBuilderConfig = client.buildConfigs().inNamespace(namespace).createOrReplace(syndoBuilderConfig);

            // create in-memory/jimfs tar file as target for build-contents tar
            Path tarFile = this.outputTar(context);
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
                        .inNamespace(namespace)
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
                bootstrapSucceeded = waitAndWatchBuild(context, namespace, client, build, logger());
            } catch (Exception e) {
                logger().error("Could not wait for build to complete: {}", e.getMessage());
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            if (!bootstrapSucceeded) {
                logger().error("Could not build {}, cannot proceed", baseImageName);
                context.setStatus(BuildContext.Status.ERROR);
            }
        } else {
            logger().info("Found image stream tag matching {} content: {}/{}", baseImageName, ist.getMetadata().getNamespace(), ist.getMetadata().getName());
        }

    }
}
