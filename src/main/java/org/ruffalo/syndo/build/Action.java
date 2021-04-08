package org.ruffalo.syndo.build;

import io.fabric8.openshift.client.OpenShiftClient;

public interface Action {

    BuildResult build(final OpenShiftClient client);

}
