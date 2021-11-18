package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import io.fabric8.openshift.api.model.BuildOutput;
import io.fabric8.openshift.api.model.BuildOutputBuilder;
import io.fabric8.openshift.api.model.BuildRequestBuilder;
import io.fabric8.openshift.api.model.BuildStatusBuilder;
import io.fabric8.openshift.api.model.CustomBuildStrategy;
import io.fabric8.openshift.api.model.CustomBuildStrategyBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.config.Cache;
import io.github.chrisruffalo.syndo.config.Component;
import io.github.chrisruffalo.syndo.config.PullSecretRef;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.BuilderAction;
import io.github.chrisruffalo.syndo.executions.actions.post.CleanupImageStream;
import io.github.chrisruffalo.syndo.model.DirSourceNode;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ComponentBuildAction extends BuilderAction {

    private static final Logger logger = LoggerFactory.getLogger(SyndoBuilderAction.class);

    private static final String UPLOAD_TARGET_PATH = "/tmp/build-input.tar.gz";
    public static final String SYNDO_OUT = SYNDO + "-fake-out";

    @Override
    public void execute(BuildContext context) {
        // get client from context
        final OpenShiftClient client = context.getClient();
        final String targetNamespace = context.getNamespace();

        // get the configuration
        final Root buildConfig = context.getConfig();

        // this is a fake output stream used as a near target for outputting the syndo build
        final ObjectMeta isMeta = new ObjectMetaBuilder().withName(SYNDO_OUT).build();
        ImageStream is = new ImageStreamBuilder()
                .withMetadata(isMeta)
                .build();
        is = client.imageStreams().inNamespace(targetNamespace).createOrReplace(is);
        // clean up the image stream when done
        context.addPostAction(new CleanupImageStream(is.getMetadata().getName()));

        final String imageStreamTagName = context.getBuilderImageName();

        // decide if the storage is enabled for this configuration
        Cache cache = null;
        boolean cacheEnabled = context.getConfig() != null && context.getConfig().getCache() != null && context.getConfig().getCache().isEnabled();
        if (cacheEnabled) {
            cache = context.getConfig().getCache();
        }
        // but allow the command to override the storage configuration
        if(context.getCommandBuild() != null && context.getCommandBuild().isCacheDisabled()) {
            cacheEnabled = false;
        }

        // create syndo build configuration
        final ObjectMetaBuilder metaBuilder = new ObjectMetaBuilder()
                .withName(SYNDO)
                .withNamespace(targetNamespace)
                .addToLabels(CacheAugmentationServiceAction.CACHE_ENABLED, Boolean.toString(cacheEnabled))
                .addToAnnotations(CacheAugmentationServiceAction.CACHE_ENABLED, Boolean.toString(cacheEnabled));

        if (cacheEnabled) {
            // name the claim
            final String claimName = cache.getClaimName();

            // check if the claim exists
            if (client.persistentVolumeClaims().inNamespace(targetNamespace).withName(claimName).get() == null) {
                // create volume claim
                final PersistentVolumeClaimBuilder claimBuilder = new PersistentVolumeClaimBuilder()
                    .withNewMetadata()
                    .withName(claimName)
                    .withNamespace(targetNamespace)
                    .endMetadata()
                    .withNewSpec()
                    .withAccessModes("ReadWriteOnce")
                    .withNewResources()
                    .addToRequests("storage", new Quantity(cache.getSize())) // todo: configure?
                    .endResources()
                    .endSpec();

                // if the storage is shared set the new access mode
                if (cache.isShared()) {
                    claimBuilder.editSpec()
                    .withAccessModes("ReadWriteMany")
                    .endSpec();
                }

                // update storage class if provided
                if (cache.getStorageClass() != null) {
                    claimBuilder.editSpec()
                    .withStorageClassName(cache.getStorageClass())
                    .endSpec();
                }

                // bind to specific volume
                if (cache.getVolumeName() != null && !cache.getVolumeName().isEmpty()) {
                    claimBuilder.editSpec()
                    .withVolumeName(cache.getVolumeName())
                    .endSpec();
                }

                client.persistentVolumeClaims().inNamespace(targetNamespace).create(claimBuilder.build());
            }

            // add volume claim name to the meta builder so that the webhook can read the annotation
            metaBuilder.addToAnnotations(CacheAugmentationServiceAction.CACHE_CLAIM_NAME, claimName);
        }

        final CustomBuildStrategyBuilder customBuildStrategyBuilder = new CustomBuildStrategyBuilder()
                .withFrom(new io.fabric8.kubernetes.api.model.ObjectReferenceBuilder().withNamespace(targetNamespace).withName(imageStreamTagName + ":latest").build())
                .withExposeDockerSocket(false)
                .withForcePull(true);

        // attach default secret to build
        if (buildConfig != null && buildConfig.getBuild() != null) {
            final io.github.chrisruffalo.syndo.config.Build buildOptions = buildConfig.getBuild();
            final PullSecretRef pullSecret = buildOptions.getPullSecret();
            if (pullSecret != null && !pullSecret.getName().isEmpty()) {
                final Secret secret = client.secrets().inNamespace(targetNamespace).withName(pullSecret.getName()).get();
                if (secret == null) {
                    logger().error("Could not find pull secret, secret with name '{}' does not exist in namespace '{}'", pullSecret.getName(), targetNamespace);
                    context.setStatus(BuildContext.Status.ERROR);
                    return;
                } else {
                    customBuildStrategyBuilder.withPullSecret(new LocalObjectReferenceBuilder().withName(pullSecret.getName()).build());
                    customBuildStrategyBuilder.addToEnv(new EnvVarBuilder().withName("DEFAULT_PULL_SECRET_IS_ALREADY_JSON").withValue("true").build());
                }
            }
        }

        // attach external secrets as env variables if they are provided
        context.getBuildOrder().forEach(node -> {
            final Component component = node.getComponent();
            if (component.getPullSecretRef() == null) {
                return;
            }
            final PullSecretRef ref = component.getPullSecretRef();
            if (ref.getName() == null || ref.getName().isEmpty()) {
                return;
            }
            final Secret secret = client.secrets().inNamespace(targetNamespace).withName(ref.getName()).get();
            if (secret == null) {
                logger().error("Could not find pull secret for component '{}', secret with name '{}' does not exist in namespace '{}'", component.getName(), ref.getName(), targetNamespace);
                context.setStatus(BuildContext.Status.ERROR);
                return;
            }
            // create environment variable with secret contents
            customBuildStrategyBuilder.addToEnv(new EnvVarBuilder()
                .withName(String.format("PULL_SECRET_%s", component.getName()))
                .withValueFrom(new EnvVarSourceBuilder()
                    .withSecretKeyRef(new SecretKeySelectorBuilder()
                        .withName(ref.getName())
                        .withKey(ref.getKey())
                        .build()
                    )
                    .build()
                )
                .build()
            );

        });
        if (BuildContext.Status.ERROR.equals(context.getStatus())) {
            return;
        }

        final BuildOutput output = new BuildOutputBuilder().withTo(new io.fabric8.kubernetes.api.model.ObjectReferenceBuilder().withNamespace(targetNamespace).withName(SYNDO_OUT).build()).build();
        final BuildConfigSpec customBuildConfigSpec = new BuildConfigSpecBuilder()
                .withNewStrategy()
                .withCustomStrategy(customBuildStrategyBuilder.build())
                .endStrategy()
                .withOutput(output)
                .build();
        BuildConfig config = new BuildConfigBuilder()
                .withSpec(customBuildConfigSpec)
                .withMetadata(metaBuilder.build())
                .build();
        config = client.buildConfigs().inNamespace(targetNamespace).createOrReplace(config);

        final Build build;
        try {
            logger.info("Building from tar: {} ({})", context.getOutputTar(), FileUtils.byteCountToDisplaySize(Files.size(context.getOutputTar())));
            if (cacheEnabled) {
                // copy the file out of the jimfs if it is not available as a File
                boolean deleteTempFile = false;
                File source;
                try {
                    source = context.getOutputTar().toFile();
                } catch (UnsupportedOperationException ex) {
                    final Path tempPath = Files.createTempFile(context.getOutputTar().getFileName().toString(), "");
                    Files.copy(context.getOutputTar(), tempPath, StandardCopyOption.REPLACE_EXISTING);
                    source = tempPath.toFile();
                    deleteTempFile = true;
                }

                final BuildRequestBuilder builder = new BuildRequestBuilder()
                        .withNewMetadata()
                        .withName(SYNDO)
                        .withNamespace(targetNamespace)
                        .addToAnnotations(CacheAugmentationServiceAction.CACHE_ENABLED, Boolean.toString(cacheEnabled))
                        .endMetadata()
                        .withNewBinary()
                        .endBinary();

                // start build
                build = client.buildConfigs()
                    .inNamespace(targetNamespace)
                    .withName(config.getMetadata().getName())
                    .instantiate(builder.build());

                // wait for build pod to become available
                PodResource<Pod> buildPod;
                do {
                    // todo: break out if too much time has passed

                    // get pod resource
                    buildPod = client.pods().inNamespace(targetNamespace).withName(String.format("%s-build", build.getMetadata().getName()));

                    // continue loop while the build pod is waiting to start
                } while (buildPod.get() == null || !buildPod.get().getStatus().getPhase().toLowerCase().contains("running"));

                // copy file in to build pod
                logger().info("Uploading {} to {}", source.toPath().normalize(), UPLOAD_TARGET_PATH);
                buildPod.file(UPLOAD_TARGET_PATH).upload(source.toPath());
                logger().info("Uploaded {}", UPLOAD_TARGET_PATH);

                // delete the temporary file on exit
                if(deleteTempFile) {
                    final boolean sourceDeleted = source.delete();
                    if (!sourceDeleted) {
                        source.deleteOnExit();
                    }
                }
            } else {
                try (final InputStream inputStream = Files.newInputStream(context.getOutputTar())) {
                    // since there is no magic happening to create the cache we can just upload file as normal
                    build = client.buildConfigs()
                        .inNamespace(targetNamespace)
                        .withName(config.getMetadata().getName())
                        .instantiateBinary()
                        .fromInputStream(inputStream);
                }
            }
        } catch (IOException e) {
            logger.error("Could not start build: {}", e.getMessage());
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }
        boolean syndoBuildSuccess;
        try {
            syndoBuildSuccess = waitAndWatchBuild(context, targetNamespace, client, build, logger);
        } catch (Exception e) {
            logger.error("Could not wait for component build to complete: {}", e.getMessage());
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }

        if (syndoBuildSuccess) {
            logger.info("Build {} finished", build.getMetadata().getName());
        } else {
            logger.error("Build {} failed", build.getMetadata().getName());
            context.setStatus(BuildContext.Status.ERROR);
        }

        // if storage is enabled the build needs a little help to be marked as completed or failed immediately
        if (cacheEnabled) {
            build.setStatus(new BuildStatusBuilder().withPhase(syndoBuildSuccess ? "Complete" : "Failed").build());
            client.builds().inNamespace(targetNamespace).withName(build.getMetadata().getName())
                    .createOrReplace(build);
        }
    }
}
