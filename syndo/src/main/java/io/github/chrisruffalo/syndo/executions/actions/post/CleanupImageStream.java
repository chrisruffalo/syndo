package io.github.chrisruffalo.syndo.executions.actions.post;

import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.executions.actions.PostAction;

public class CleanupImageStream implements PostAction {

    private final String imageStreamToClean;

    public CleanupImageStream(final String imageStreamToClean) {
        this.imageStreamToClean = imageStreamToClean;
    }

    @Override
    public void execute(BuildContext context) {
        final OpenShiftClient client = context.getClient();

        // delete the resource
        client.imageStreams().inNamespace(context.getNamespace()).withName(imageStreamToClean).delete();
    }

    @Override
    public boolean isApplicable(BuildContext context) {
        final OpenShiftClient client = context.getClient();

        // if no client we cannot do this post action
        if (client == null) {
            return false;
        }

        // if the image stream doesn't exist, return false
        return client.imageStreams().inNamespace(context.getNamespace()).withName(imageStreamToClean).get() != null;
    }
}
