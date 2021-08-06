package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.IntOrStringBuilder;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.ServiceAccountBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.ServiceSpecBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfiguration;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.MutatingWebhookConfigurationBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperations;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.RuleWithOperationsBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.ServiceReferenceBuilder;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.WebhookClientConfig;
import io.fabric8.kubernetes.api.model.admissionregistration.v1.WebhookClientConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfig;
import io.fabric8.openshift.api.model.DeploymentConfigBuilder;
import io.fabric8.openshift.api.model.DeploymentConfigSpecBuilder;
import io.fabric8.openshift.api.model.DeploymentTriggerImageChangeParamsBuilder;
import io.fabric8.openshift.api.model.DeploymentTriggerPolicyBuilder;
import io.fabric8.openshift.api.model.Project;
import io.fabric8.openshift.api.model.ProjectBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandCache;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.permissions.PermTuple;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Builds the webhook service into the correct namespace (as specified in the storage configuration) if needed
 */
public class CacheAugmentationServiceAction extends SyndoBuilderAction {

    public static final String CACHE_ENABLED = "syndo/cache-enabled";
    public static final String CACHE_CLAIM_NAME = "syndo/cache-claim-name";
    private static final String BOOTSTRAP_RESOURCE_PATH = "containers/cache-augmentation";
    public static final String WEBHOOK_SERVICE_NAME = "cache-augmentation";

    private static final Path SYNDO_RESOURCE_PATH = bootstrapSyndoResourcePath(BOOTSTRAP_RESOURCE_PATH);

    private String savedImageName;

    @Override
    protected Path getResourcePath(BuildContext context) {
        final Command command = context.getCommand();
        final CommandCache storage;
        if (command != null && command.getCache() != null) {
            storage = command.getCache();
        } else {
            return SYNDO_RESOURCE_PATH;
        }

        Path storageDirectory = storage.getStorageResourcePath();
        if (storageDirectory == null || !Files.exists(storageDirectory)) {
            storageDirectory = SYNDO_RESOURCE_PATH;
        } else {
            storageDirectory = storageDirectory.normalize().toAbsolutePath();
        }
        return storageDirectory;
    }

    @Override
    protected String getImageName(BuildContext context) {
        return WEBHOOK_SERVICE_NAME;
    }

    @Override
    protected String targetNamespace(BuildContext context) {
        if (context != null && context.getCommand() != null && context.getCommand().getCache() != null) {
            return context.getCommand().getCache().getNamespace();
        }
        return CommandCache.DEFAULT_CACHE_NAMESPACE;
    }

    @Override
    protected boolean isForced(BuildContext context) {
        return context != null && context.getCommand() != null && context.getCommand().getCache() != null && context.getCommand().getCache().isForceCache();
    }

    @Override
    protected Path outputTar(BuildContext context) {
        // no output tar
        return null;
    }

    @Override
    protected void saveImageName(BuildContext context, String imageName) {
        this.savedImageName = imageName;
    }

    @Override
    public void execute(BuildContext context) {
        // get client from context
        final OpenShiftClient client = context.getClient();
        final String namespace = this.targetNamespace(context);
        logger().debug("Storage service target namespace: {}", namespace);

        // create the infra namespace if it does not exist
        final Project project = client.projects().withName(namespace).get();
        if (project == null) {
            client.projects().createOrReplace(new ProjectBuilder()
                .withMetadata(
                    new ObjectMetaBuilder()
                    .withName(namespace)
                    .build()
                )
                .build()
            );
        }

        // check permissions
        this.permissionCheck(client, context, namespace,
            new PermTuple("create", "pod"),
            new PermTuple("create", "service"),
            new PermTuple("create", "deploymentconfig"),
            new PermTuple("create", "serviceaccount"),
            new PermTuple("create", "mutatingwebhookconfiguration")
        );
        if (!context.getStatus().equals(BuildContext.Status.OK)) {
            return;
        }

        // defer to syndo embedded resource
        super.execute(context);

        if (this.savedImageName == null || this.savedImageName.isEmpty()) {
            context.setStatus(BuildContext.Status.ERROR);
            logger().error("No image built/saved for storage webhook");
            return;
        }

        // create service account for running storage webhook
        final ServiceAccount account = new ServiceAccountBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                .withName(WEBHOOK_SERVICE_NAME)
                .withNamespace(namespace)
                .build()
            )
            .build();
        client.serviceAccounts().inNamespace(namespace).createOrReplace(account);

        // create/update service for webhook endpoint
        final Service serviceBuilder = new ServiceBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                .withNamespace(namespace)
                .withName(WEBHOOK_SERVICE_NAME)
                .addToAnnotations("service.beta.openshift.io/serving-cert-secret-name", WEBHOOK_SERVICE_NAME + "-tls") // beta for openshift 4
                .addToAnnotations("service.alpha.openshift.io/serving-cert-secret-name", WEBHOOK_SERVICE_NAME + "-tls") // alpha for openshift 3
                .build()
            )
            .withSpec(
                new ServiceSpecBuilder()
                .addToPorts(
                    new ServicePortBuilder()
                    .withName("https")
                    .withProtocol("TCP")
                    .withPort(8443)
                    .withTargetPort(new IntOrStringBuilder().withIntVal(8443).build())
                    .build()
                )
                .addToSelector("application", WEBHOOK_SERVICE_NAME)
                .build()
            )
            .build();
        client.services().inNamespace(namespace).createOrReplace(serviceBuilder);

        // create/update deployment configuration for webhook endpoint
        final DeploymentConfig deploymentConfig = new DeploymentConfigBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                .withNamespace(namespace)
                .withName(WEBHOOK_SERVICE_NAME)
                .build()
            )
            .withSpec(
                new DeploymentConfigSpecBuilder()
                .withReplicas(1)
                .withTemplate(
                    new PodTemplateSpecBuilder()
                    .withMetadata(
                        new ObjectMetaBuilder()
                        .withNamespace(namespace)
                        .withName(WEBHOOK_SERVICE_NAME)
                        .addToLabels("application", WEBHOOK_SERVICE_NAME)
                        .build()
                    )
                    .withSpec(
                        new PodSpecBuilder()
                        .withServiceAccountName(account.getMetadata().getName())
                        .addToContainers(
                            new ContainerBuilder()
                            .withName(WEBHOOK_SERVICE_NAME)
                            .withImage(this.savedImageName + ":latest")
                            .addToPorts(
                                new ContainerPortBuilder()
                                .withName("https")
                                .withContainerPort(8443)
                                .withProtocol("TCP")
                                .build()
                            )
                            .withReadinessProbe(
                                new ProbeBuilder()
                                .withHttpGet(
                                    new HTTPGetActionBuilder()
                                    .withPath("/health")
                                    .withScheme("HTTPS")
                                    .withPort(new IntOrStringBuilder().withIntVal(8443).build())
                                    .build()
                                )
                                .build()
                            )
                            .withLivenessProbe(
                                new ProbeBuilder()
                                .withHttpGet(
                                    new HTTPGetActionBuilder()
                                    .withPath("/health")
                                    .withScheme("HTTPS")
                                    .withPort(new IntOrStringBuilder().withIntVal(8443).build())
                                    .build()
                                )
                                .build()
                            )
                            .addToVolumeMounts(
                                new VolumeMountBuilder()
                                .withName("tls-volume")
                                .withMountPath("/opt/app-root/src/ssl")
                                .build()
                            )
                            .build() // end container
                        )
                        .withVolumes(
                            new VolumeBuilder()
                            .withName("tls-volume")
                            .withSecret(
                                new SecretVolumeSourceBuilder()
                                .withSecretName(WEBHOOK_SERVICE_NAME + "-tls")
                                .build()
                            )
                            .build()
                        )
                        .build() // end pod spec
                    )
                    .build() // end template
                )
                .withTriggers(
                    new DeploymentTriggerPolicyBuilder()
                    .withType("ConfigChange")
                    .build(),
                    new DeploymentTriggerPolicyBuilder()
                    .withType("ImageChange")
                    .withImageChangeParams(
                        new DeploymentTriggerImageChangeParamsBuilder()
                        .addNewContainerName(WEBHOOK_SERVICE_NAME)
                        .withAutomatic(true)
                        .withFrom(new ObjectReferenceBuilder().withName(this.savedImageName + ":latest").build())
                        .build()
                    )
                    .build()
                )
                .build() // end deployment config spec
            )
            .build(); // end deployment config
        client.deploymentConfigs().inNamespace(namespace).createOrReplace(deploymentConfig);

        // finally, create webhook
        createWebhook(client, namespace, context);

        // wait for new deployment/rc to be ready/complete ?
    }

    private void createWebhook(final OpenShiftClient client, final String namespace, final BuildContext context) {
        // get secret from service cert and use that to verify the webhook call
        Secret secret = null;
        long startTime = System.currentTimeMillis();
        while( secret == null && (System.currentTimeMillis() - startTime) < 5000) { // wait for up to 5 seconds for the secret
            secret = client.secrets().inNamespace(namespace).withName(WEBHOOK_SERVICE_NAME + "-tls").get();
        }
        if (secret == null) {
            logger().warn("No secret certificate for target service... skipping storage augmentation webhook creation");
            return;
        }
        final String tlsCrtData = secret.getData().get("tls.crt");

        final WebhookClientConfig clientConfig = new WebhookClientConfigBuilder()
            .withCaBundle(tlsCrtData)
            .withService(
                new ServiceReferenceBuilder()
                .withName(WEBHOOK_SERVICE_NAME)
                .withNamespace(namespace)
                .withPath("/build-cache-mutator")
                .withPort(8443)
                .build()
            )
            .build();

        // apply to created pods
        final RuleWithOperations rule = new RuleWithOperationsBuilder()
            .addToOperations("CREATE")
            .addToApiGroups("*")
            .addToApiVersions("*")
            .addToResources("pods")
            .build();

        final MutatingWebhookConfiguration webhookConfiguration = new MutatingWebhookConfigurationBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                .withName(WEBHOOK_SERVICE_NAME)
                .withNamespace(context.getNamespace())
                .build()
            )
            .addNewWebhook()
            .withAdmissionReviewVersions("v1beta1", "v1")
            .withName(String.format("%s.%s.svc.cluster.local", WEBHOOK_SERVICE_NAME, this.targetNamespace(context)))
            .withClientConfig(clientConfig)
            .withRules(rule)
            .withNamespaceSelector(
                new LabelSelectorBuilder()
                .addToMatchLabels(CACHE_ENABLED, "true")
                .build()
            )
            .withMatchPolicy("Equivalent")
            .withFailurePolicy("Ignore") // ignore failures and continue with admission because it just means the cache will be disabled for that pod
            .withSideEffects("None")
            .endWebhook()
            .build();

        // create or replace webhook configuration
        client.admissionRegistration().v1().mutatingWebhookConfigurations().inNamespace(context.getNamespace()).createOrReplace(webhookConfiguration);
    }
}
