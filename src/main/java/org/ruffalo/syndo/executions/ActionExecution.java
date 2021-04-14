package org.ruffalo.syndo.executions;

import org.ruffalo.syndo.actions.Action;
import org.ruffalo.syndo.actions.BuildContext;
import org.ruffalo.syndo.exceptions.SyndoException;

import java.util.List;

public abstract class ActionExecution extends Execution {

    protected abstract List<Action> getBuildActions(final BuildContext context);

    public abstract BuildContext createContext() throws SyndoException;

    protected ExecutionResult executeActions(final BuildContext context) {
        // create result
        ExecutionResult result = new ExecutionResult();

        // get build actions from implementing execution
        final List<Action> buildActions = getBuildActions(context);

        // execute actions and break on failed action
        for (final Action action : buildActions) {
            action.build(context);
            if (!BuildContext.Status.OK.equals(context.getStatus())) {
                switch (context.getStatus()) {
                    case DONE:
                        logger().info("Build finished");
                        break;
                    case INVALID:
                        logger().warn("Build finished due to invalid state or configuration");
                        result.setExitCode(1);
                        break;
                    case ERROR:
                        logger().error("Build finished with errors");
                        result.setExitCode(1);
                        break;
                    default:
                }
                break;
            }
        }

        return result;
    }

    @Override
    public ExecutionResult execute() {
        try {
            // this is the default behavior
            return executeActions(this.createContext());
        } catch (SyndoException ex) {
            logger().error(ex.getMessage());
            return new ExecutionResult(1);
        }
    }
}
