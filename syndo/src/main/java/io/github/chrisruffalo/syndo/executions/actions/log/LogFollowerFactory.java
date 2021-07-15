package io.github.chrisruffalo.syndo.executions.actions.log;

import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;

public class LogFollowerFactory {

    public static LogFollower getLogFollower(final LogWatch watch, final BuildContext context) {
        // if the context is not null then we can use the context to get the configuration
        if (context != null) {
            final CommandOpenShift occ = context.getOpenshiftCommand();
            if (occ != null) {
                final String buildLogType = occ.getBuildLogType();
                if (buildLogType != null && !buildLogType.isEmpty()) {
                    // look for parsed types
                    switch (buildLogType.trim().toLowerCase()) {
                        case "graphical":
                            return new GraphicalLogFollower(watch);
                        case "short":
                            return new ShortLogFollower(watch);
                        case "none":
                            return new NoneLogFollower(watch);
                        default:
                            break;
                    }
                }
            }
        }

        // use the full log follower by default
        return new FullLogFollower(watch);
    }

}
