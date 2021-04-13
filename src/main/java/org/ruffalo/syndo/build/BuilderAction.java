package org.ruffalo.syndo.build;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.openshift.api.model.Build;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public abstract class BuilderAction extends BaseAction {

    public static final String SYNDO = "syndo";

    public static final String LABEL_PREFIX = "syndo/";
    public static final String CREATED_FOR = LABEL_PREFIX + "created-for";

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

    protected boolean waitAndWatchBuild(final String namespaceName, final OpenShiftClient client, final Build build, final Logger logger) throws Exception {
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

        // ensure build pod is marked as created for the same purpose
        if (build.getMetadata().getLabels().get(CREATED_FOR) != null) {
            client.pods().inNamespace(namespaceName).withName(buildPodName).edit(pod -> {
                // do not edit null pods (doubt you can, but still)
                if (pod == null) {
                    return null;
                }

                final Map<String, String> labels = pod.getMetadata().getLabels();
                labels.put(CREATED_FOR, build.getMetadata().getLabels().get(CREATED_FOR));
                pod.getMetadata().setLabels(labels);
                return pod;
            });
        }

        // if the pod did not become ready (but after it has the right label) exit
        if (!ready) {
            return false;
        }

        // grab the logs when the pod is ready
        final LogWatch log = client.pods().inNamespace(namespaceName).withName(buildPodName).watchLog();
        final Thread t = new Thread(() -> {
            final InputStream stream = log.getOutput();
            try {
                IOUtils.copy(stream, System.out);
            } catch (IOException e) {
                // nothing we can do about it
            } finally {
                // close the log
                log.close();
            }
        });
        t.start();

        // wait for the final status
        boolean succeeded = waitForStatus("succeeded", namespaceName, client, buildPodName, logger);

        // wait for log thread to finish
        t.join();

        // if the build did not succeed quit
        return succeeded;
    }

}
