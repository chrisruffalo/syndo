package io.github.chrisruffalo.syndo.executions.actions;

/**
 * An extension of Action to allow post actions to determine if
 * they are still applicable and should be run after all the other
 * steps are complete.
 */
public interface PostAction extends Action {

    /**
     * Determine, based on the context, if this post
     * action is still applicable.
     *
     * @param context the current build state
     * @return true if the post step should be executed, false otherwise
     */
    boolean isApplicable(BuildContext context);

}
