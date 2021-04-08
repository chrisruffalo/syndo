package org.ruffalo.syndo;

import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.build.BuildResult;
import org.ruffalo.syndo.build.BuilderAction;
import org.ruffalo.syndo.build.ComponentBuildAction;
import org.ruffalo.syndo.build.SyndoBuiderAction;

import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) {
        try (final OpenShiftClient client = new DefaultOpenShiftClient()) {
            final SyndoBuiderAction action1 = new SyndoBuiderAction(BuilderAction.SYNDO);
            final BuildResult r1 = action1.build(client);
            if (r1.getStatus().equals(BuildResult.Status.FAILED)) {
                System.exit(1);
            }

            final ComponentBuildAction action2 = new ComponentBuildAction(BuilderAction.SYNDO, Paths.get("./sample-build"));
            final BuildResult r2 = action2.build(client);
            if (r2.getStatus().equals(BuildResult.Status.FAILED)) {
                System.exit(1);
            }
        }
    }

}
