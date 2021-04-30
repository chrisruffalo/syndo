package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.api.model.BuildConfig;
import io.fabric8.openshift.api.model.BuildConfigBuilder;
import io.fabric8.openshift.api.model.BuildConfigSpec;
import io.fabric8.openshift.api.model.BuildConfigSpecBuilder;
import io.fabric8.openshift.api.model.BuildOutput;
import io.fabric8.openshift.api.model.BuildOutputBuilder;
import io.fabric8.openshift.api.model.CustomBuildStrategy;
import io.fabric8.openshift.api.model.CustomBuildStrategyBuilder;
import io.fabric8.openshift.api.model.ImageStream;
import io.fabric8.openshift.api.model.ImageStreamBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.BuilderAction;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ComponentBuildAction extends BuilderAction {

    private static final Logger logger = LoggerFactory.getLogger(SyndoBuilderAction.class);
    public static final String SYNDO_OUT = SYNDO + "-fake-out";

    @Override
    public void build(BuildContext context) {
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

        final String imageStreamTagName = context.getBuilderImageName();

        // create syndo build configuration
        final ObjectMeta meta = new ObjectMetaBuilder().withName(SYNDO).build();
        final CustomBuildStrategyBuilder customBuildStrategyBuilder = new CustomBuildStrategyBuilder()
                .withFrom(new io.fabric8.kubernetes.api.model.ObjectReferenceBuilder().withNamespace(targetNamespace).withName(imageStreamTagName + ":latest").build())
                .withForcePull(true);

        final CustomBuildStrategy customBuildStrategy = customBuildStrategyBuilder.build();
        if (buildConfig != null && buildConfig.getBuild() != null) {
            final io.github.chrisruffalo.syndo.config.Build buildOptions = buildConfig.getBuild();
            final String pullSecret = buildOptions.getPullSecret();
            if (pullSecret != null && !pullSecret.isEmpty()) {
                final Secret secret = client.secrets().inNamespace(targetNamespace).withName(pullSecret).get();
                if (secret == null) {
                    logger().error("Could not find pull secret, secret with name '{}' does not exist", pullSecret);
                    context.setStatus(BuildContext.Status.ERROR);
                    return;
                } else {
                    customBuildStrategy.setPullSecret(new LocalObjectReferenceBuilder().withName(pullSecret).build());
                }
            }
        }
        final BuildOutput output = new BuildOutputBuilder().withTo(new io.fabric8.kubernetes.api.model.ObjectReferenceBuilder().withNamespace(targetNamespace).withName(SYNDO_OUT).build()).build();
        final BuildConfigSpec customBuildConfigSpec = new BuildConfigSpecBuilder()
                .withNewStrategy()
                .withCustomStrategy(customBuildStrategy)
                .endStrategy()
                .withOutput(output)
                .build();
        BuildConfig config = new BuildConfigBuilder()
                .withSpec(customBuildConfigSpec)
                .withMetadata(meta)
                .build();
        config = client.buildConfigs().inNamespace(targetNamespace).createOrReplace(config);

        final Build build;
        try (final InputStream inputStream = Files.newInputStream(context.getOutputTar())){
            logger.info("Building from tar: {} ({})", context.getOutputTar(), FileUtils.byteCountToDisplaySize(Files.size(context.getOutputTar())));
            build = client.buildConfigs()
                    .inNamespace(targetNamespace)
                    .withName(config.getMetadata().getName())
                    .instantiateBinary().fromInputStream(inputStream);
        } catch (IOException e) {
            logger.error("Could not start build: {}", e.getMessage());
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }

        boolean syndoBuildSuccess;
        try {
            syndoBuildSuccess = waitAndWatchBuild(targetNamespace, client, build, logger);
        } catch (Exception e) {
            logger.error("Could not wait for component build to complete: {}", e.getMessage());
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }

        // delete fake output image stream since it was just used to temporarily provide output credentials for the
        // buildah custom build process
        if (client.imageStreams().inNamespace(targetNamespace).withName(SYNDO_OUT).get() != null) {
            client.imageStreams().inNamespace(targetNamespace).withName(SYNDO_OUT).delete();
        }

        if (syndoBuildSuccess) {
            logger.info("Build {} finished", build.getMetadata().getName());
        } else {
            logger.error("Build {} failed", build.getMetadata().getName());
            context.setStatus(BuildContext.Status.ERROR);
        }
    }
}
