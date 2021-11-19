package io.github.chrisruffalo.syndo.executions.actions;

public interface Action {

    /**
     * Execute the build action given the state from the context
     *
     * @param context the current build process state/context
     */
    void execute(final BuildContext context);

}
