package io.github.chrisruffalo.syndo.executions.actions;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.executions.actions.log.LogFollower;
import io.github.chrisruffalo.syndo.executions.actions.log.LogFollowerFactory;
import org.slf4j.Logger;

public abstract class BuilderAction extends BaseAction {

    public static final String SYNDO = "syndo";

    protected boolean waitForStatus(final String statusToWaitFor, final String namespaceName, final OpenShiftClient client, final String podName, final Logger logger) {
        Pod pod = client.pods().inNamespace(namespaceName).withName(podName).get();
        while (pod == null) {
            pod = client.pods().inNamespace(namespaceName).withName(podName).get();
        }
        while (pod != null) {
            final String phase = pod.getStatus().getPhase().toLowerCase();
            if (phase.equals(statusToWaitFor)) {
                return true;
            } else if(phase.contains("error") || phase.contains("terminate") || phase.contains("fail")) {
                return false;
            }
            pod = client.pods().inNamespace(namespaceName).withName(pod.getMetadata().getName()).get();
        }
        return false;
    }

    protected boolean waitAndWatchBuild(final BuildContext context, final String namespaceName, final OpenShiftClient client, final Build build, final Logger logger) throws Exception {
        logger.info("Started build: {}", build.getMetadata().getName());

        // wait for build to complete by looking up pod
        final String buildPodName = build.getMetadata().getName() + "-build";

        // wait for the pod to become ready
        boolean ready = waitForStatus("running", namespaceName, client, buildPodName, logger);
        if (!ready) {
            logger.error("Pod {} did not become ready", buildPodName);
            // delete the pod in this case
            if (client.pods().inNamespace(namespaceName).withName(buildPodName).get() != null) {
                client.pods().inNamespace(namespaceName).withName(buildPodName).delete();
            }
        }

        // if the pod did not become ready (but after it has the right label) exit
        if (!ready) {
            return false;
        }

        // create a log watcher that will get logs from the running event and then use the log factory
        // to direct the logs to the appropriate parsing/output mechanism
        final LogWatch log = client.pods().inNamespace(namespaceName).withName(buildPodName).watchLog();
        final LogFollower follower = LogFollowerFactory.getLogFollower(log, context);
        final Thread thread = new Thread(follower);

        // start the thread that will follow the logs
        thread.start();

        // wait for the final status
        boolean succeeded = waitForStatus("succeeded", namespaceName, client, buildPodName, logger);

        // wait for log thread to finish
        thread.join();

        if (succeeded) {
            logger.info("Build complete: {}", build.getMetadata().getName());
        }

        // return the build status
        return succeeded;
    }

}
