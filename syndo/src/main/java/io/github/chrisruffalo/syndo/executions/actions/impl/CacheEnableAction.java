package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.rbac.PolicyRuleBuilder;
import io.fabric8.kubernetes.api.model.rbac.Role;
import io.fabric8.kubernetes.api.model.rbac.RoleBinding;
import io.fabric8.kubernetes.api.model.rbac.RoleBindingBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleBuilder;
import io.fabric8.kubernetes.api.model.rbac.RoleRefBuilder;
import io.fabric8.kubernetes.api.model.rbac.SubjectBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.config.Cache;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.BuilderAction;
import io.github.chrisruffalo.syndo.permissions.PermTuple;

/**
 * Enables the target (of the build) namespace to be "seen" and acted upon by the cache augmentation webhook
 */
public class CacheEnableAction extends BuilderAction {

    @Override
    public void execute(BuildContext context) {
        // need configuration
        if (context.getConfig() == null) {
            return;
        }

        // get storage configuration
        final Cache cache = context.getConfig().getCache();
        if (cache == null || !cache.isEnabled()) {
            logger().debug("Cache augmentation is not enabled");
            return;
        }
        logger().info("Cache augmentation is enabled");

        // get client
        final OpenShiftClient client = context.getClient();

        // check permissions
        this.permissionCheck(client, context, context.getNamespace(),
            new PermTuple("create", "persistentvolumeclaim"),
            new PermTuple("create", "role"),
            new PermTuple("create", "rolebinding")
        );
        if (!context.getStatus().equals(BuildContext.Status.OK)) {
            return;
        }

        // ensure that storage is enabled on the namespace
        final Namespace namespace = client.namespaces().withName(context.getNamespace()).get();
        if (namespace == null) {
            // not sure how we would get here
            logger().error("Attempting to run in a namespace ('{}') that does not exist...", context.getNamespace());
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }
        client.namespaces()
            .withName(context.getNamespace())
            .createOrReplace(
                new NamespaceBuilder()
                    .editOrNewMetadataLike(
                        new ObjectMetaBuilder()
                            .withName(context.getNamespace())
                            .addToLabels(CacheAugmentationServiceAction.CACHE_ENABLED, "true")
                            .build()
                    ).endMetadata()
            .build()
        );

        // ensure that the mutating web hook service can access this namespace
        // create cluster role tailored to the needs of the storage service
        final Role role = new RoleBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                .withName(CacheAugmentationServiceAction.WEBHOOK_SERVICE_NAME)
                .build()
            )
            .addToRules(
                new PolicyRuleBuilder()
                .addToApiGroups("*")
                .addToResources(
                    "builds",
                    "buildconfigs"
                )
                .addToVerbs(
                    "get",
                    "list"
                )
                .build()
            )
            .build();
        client.rbac().roles().inNamespace(context.getNamespace()).createOrReplace(role);

        // create relevant cluster role binding
        final RoleBinding binding = new RoleBindingBuilder()
            .withMetadata(
                new ObjectMetaBuilder()
                .withName(CacheAugmentationServiceAction.WEBHOOK_SERVICE_NAME)
                .build()
            )
            .withRoleRef(
                new RoleRefBuilder()
                .withKind(role.getKind())
                .withName(role.getMetadata().getName())
                .build()
            )
            .withSubjects(
                new SubjectBuilder()
                .withKind("ServiceAccount")
                .withNamespace("syndo-infra") // todo: uh.... how to expose this? config probably...
                .withName(CacheAugmentationServiceAction.WEBHOOK_SERVICE_NAME)
                .build()
            )
            .build();
        client.rbac().roleBindings().inNamespace(context.getNamespace()).createOrReplace(binding);
    }
}
