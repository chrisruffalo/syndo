package io.github.chrisruffalo.syndo.permissions;

import io.fabric8.kubernetes.api.model.authorization.v1beta1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1beta1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class PermissionChecker {

    private static final Logger logger = LoggerFactory.getLogger(PermissionChecker.class);

    private final OpenShiftClient client;

    private final String namespace;

    public PermissionChecker(final OpenShiftClient client, final String namespace) {
        this.client = client;
        this.namespace = namespace;
    }

    public List<PermTuple> check(final PermTuple... permissionChecks) {
        if (permissionChecks == null || permissionChecks.length == 0) {
            return Collections.emptyList();
        }

        final VersionInfo info = client.getVersion();
        final String major = info.getMajor();
        final String minor = info.getMinor();
        logger.debug("Verifying Permissions in '{}' (OpenShift {}.{})", namespace, major, minor);

        final List<PermTuple> missingPermissions = new LinkedList<>();

        for (PermTuple permissionCheck : permissionChecks) {
            switch (major) {
                case "3":
                    if(!checkPermOpenShift3(client, namespace, permissionCheck.getVerb(), permissionCheck.getResource())) {
                        missingPermissions.add(permissionCheck);
                    }
                    break;
                case "4":
                    if(!checkPermOpenShift4(client, namespace, permissionCheck.getVerb(), permissionCheck.getResource())) {
                        missingPermissions.add(permissionCheck);
                    }
                    break;
            }
        }

        return missingPermissions;
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
                .withVerb(verb)
                .withResource(resource)
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
                .withVerb(verb)
                .withResource(resource)
                .withNamespace(namespace)
                .endResourceAttributes()
                .endSpec()
                .build();
        reviewB1 = client.authorization().v1().selfSubjectAccessReview().create(reviewB1);
        return reviewB1.getStatus().getAllowed();
    }

}
