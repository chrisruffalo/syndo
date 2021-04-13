package org.ruffalo.syndo.build;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.openshift.api.model.*;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.io.FileUtils;
import org.ruffalo.syndo.resources.SyndoTarCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ComponentBuildAction extends BuilderAction {

    private static final Logger logger = LoggerFactory.getLogger(SyndoBuiderAction.class);

    public static final String SYNDO_OUT = SYNDO + "-fake-out";

    private final String targetNamespace;
    private final Path targetTar;

    public ComponentBuildAction(final String targetNamespace, final Path targetTar) {
        this.targetNamespace = targetNamespace;
        this.targetTar = targetTar;
    }

    @Override
    public void build(BuildContext context) {
        // get client from context
        final OpenShiftClient client = context.getClient();

        // start result
        final BuildResult result = new BuildResult();

        Namespace namespace = client.namespaces().withName(this.targetNamespace).get();
        if (namespace == null) {
            final ObjectMeta metadata = new ObjectMeta();
            metadata.setName(this.targetNamespace);
            namespace = client.namespaces().createOrReplace(new NamespaceBuilder().withMetadata(metadata).build());
        }

        // this is a fake output stream used as a near target for outputting the syndo build
        final ObjectMeta isMeta = new ObjectMetaBuilder().withName(SYNDO_OUT).build();
        ImageStream is = new ImageStreamBuilder()
                .withMetadata(isMeta)
                .build();
        is = client.imageStreams().inNamespace(targetNamespace).createOrReplace(is);

        // create syndo build configuration
        final ObjectMeta meta = new ObjectMetaBuilder().withName(SYNDO).addToLabels(CREATED_FOR, SYNDO).build();
        final CustomBuildStrategy customBuildStrategy = new CustomBuildStrategyBuilder()
                .withFrom(new io.fabric8.kubernetes.api.model.ObjectReferenceBuilder().withNamespace(targetNamespace).withName(SyndoBuiderAction.SYNDO_BUILDER_LATEST).build())
                .withForcePull(true)
                .build();
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
        try {
            logger.info("Building from tar: {} ({})", this.targetTar, FileUtils.byteCountToDisplaySize(Files.size(this.targetTar)));
            build = client.buildConfigs()
                    .inNamespace(targetNamespace)
                    .withName(config.getMetadata().getName())
                    .instantiateBinary().fromInputStream(Files.newInputStream(this.targetTar));
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
            logger.info("Build {} succeeded", build.getMetadata().getName());
        } else {
            logger.error("Build {} failed", build.getMetadata().getName());
            context.setStatus(BuildContext.Status.ERROR);
        }
    }
}
