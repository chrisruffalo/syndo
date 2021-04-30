package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.kubernetes.api.model.authorization.v1beta1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1beta1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.executions.actions.BaseAction;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;

import java.util.LinkedList;
import java.util.List;

public class PermissionCheckAction extends BaseAction {

    private static class PermTuple {
        private final String verb;
        private final String resource;

        public PermTuple(final String verb, final String resource) {
            this.verb = verb;
            this.resource = resource;
        }

        public String getVerb() {
            return verb;
        }

        public String getResource() {
            return resource;
        }
    }

    @Override
    public void build(BuildContext context) {
        final CommandOpenShift commandOpenShift = context.getCommandBuild() != null ? context.getCommandBuild() : context.getCommand().getBootstrap();
        if (commandOpenShift == null) {
            logger().warn("Permission Checks should not be performed outside of an OpenShift context");
            return;
        }

        // leave during dry run
        if (commandOpenShift.isDryRun()) {
            return;
        }

        // get the openshift client
        OpenShiftClient client = context.getClient();
        if (client == null) {
            logger().error("No OpenShift client present in context");
            context.setStatus(BuildContext.Status.ERROR);
            return;
        }

        final String namespace = context.getNamespace();

        final VersionInfo info = client.getVersion();
        final String major = info.getMajor();
        final String minor = info.getMinor();
        logger().info("Verifying Permissions in '{}' (OpenShift {}.{})", namespace, major, minor);

        // these things are required by syndo
        final List<PermTuple> permissionChecks = new LinkedList<>();
        permissionChecks.add(new PermTuple("create", "buildconfigs"));
        permissionChecks.add(new PermTuple("create", "build"));
        permissionChecks.add(new PermTuple("create", "imagestream"));
        permissionChecks.add(new PermTuple("create", "imagestreamtags"));

        final List<PermTuple> missingPermissions = new LinkedList<>();

        for (PermTuple permissionCheck : permissionChecks) {
            switch (major) {
                case "3":
                    if(!checkPermOpenShift3(client, namespace, permissionCheck.getVerb(), permissionCheck.getResource())) {
                        missingPermissions.add(permissionCheck);
                    } else {
                        logger().debug("Has permission '{} {}' in namespace '{}'", permissionCheck.getVerb(), permissionCheck.getResource(), namespace);
                    }
                case "4":
                    if(!checkPermOpenShift4(client, namespace, permissionCheck.getVerb(), permissionCheck.getResource())) {
                        missingPermissions.add(permissionCheck);
                    } else {
                        logger().debug("Has permission '{} {}' in namespace '{}'", permissionCheck.getVerb(), permissionCheck.getResource(), namespace);
                    }
                    break;
                default:
                    logger().info("Unrecognized OpenShift major version");
                    context.setStatus(BuildContext.Status.ERROR);
            }
            // if the status flips to error we are done
            if (BuildContext.Status.ERROR.equals(context.getStatus())) {
                break;
            }
        }

        // report on missing permissions
        if (!missingPermissions.isEmpty()) {
            context.setStatus(BuildContext.Status.ERROR);
            missingPermissions.forEach(missing -> {
                logger().error("Missing permission '{} {}' in namespace '{}'", missing.getVerb(), missing.getResource(), namespace);
            });
        }
    }

    /**
     * Checks the "can I {verb} {resource}" permission like `oc auth can-i` in the given namespace. This method uses
     * beta versions of the resources and is for OpenShift 3.
     *
     * @param client the openshift client
     * @param namespace the namespace to check in
     * @param verb the verb to check
     * @param resource the resource to act on
     * @return true if the permission is available, false if denied
     */
    private boolean checkPermOpenShift3(final OpenShiftClient client, final String namespace, final String verb, final String resource) {
        SelfSubjectAccessReview reviewV1Beta = new SelfSubjectAccessReviewBuilder()
                .withNewSpec()
                .withNewResourceAttributes()
                .withVerb("create")
                .withResource("builds")
                .withNamespace(namespace)
                .endResourceAttributes()
                .endSpec()
                .build();
        reviewV1Beta = client.authorization().v1beta1().selfSubjectAccessReview().create(reviewV1Beta);
        return reviewV1Beta.getStatus().getAllowed();
    }

    /**
     * Checks the "can I {verb} {resource}" permission like `oc auth can-i` in the given namespace. This method uses v1 resources
     * and is for OpenShift 4.
     *
     * @param client the openshift client
     * @param namespace the namespace to check in
     * @param verb the verb to check
     * @param resource the resource to act on
     * @return true if the permission is available, false if denied
     */
    private boolean checkPermOpenShift4(final OpenShiftClient client, final String namespace, final String verb, final String resource) {
        io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview reviewB1 = new io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder()
                .withNewSpec()
                .withNewResourceAttributes()
                .withVerb("create")
                .withResource("builds")
                .withNamespace(namespace)
                .endResourceAttributes()
                .endSpec()
                .build();
        reviewB1 = client.authorization().v1().selfSubjectAccessReview().create(reviewB1);
        return reviewB1.getStatus().getAllowed();
    }
}
