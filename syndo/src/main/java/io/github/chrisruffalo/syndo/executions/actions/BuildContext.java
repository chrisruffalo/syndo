package io.github.chrisruffalo.syndo.executions.actions;

import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.cmd.Command;
import io.github.chrisruffalo.syndo.cmd.CommandBuild;
import io.github.chrisruffalo.syndo.cmd.CommandOpenShift;
import io.github.chrisruffalo.syndo.config.Component;
import io.github.chrisruffalo.syndo.config.Root;
import io.github.chrisruffalo.syndo.model.DirSourceNode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The build context is the carrier for information and state
 * and is carried between Actions and PostActions.
 */
public class BuildContext {

    /**
     * Status is the state of the system between actions. The execution
     * will check the status after every action to see if it should proceed.
     */
    public enum Status {
        /**
         * The current action and all previous actions completed successfully.
         * Subsequent actions can be executed.
         */
        OK,
        /**
         * The current action has indicated that action execution may end. (Or all
         * actions have ended.) No further actions should be executed. (Post actions
         * will still be executed.)
         */
        DONE,
        /**
         * The current action left the build process in an invalid state. The execution
         * should halt. Post actions can be performed.
         */
        INVALID,
        /**
         * The current action caused an error. No further executions should be performed.
         * Post actions can be executed.
         */
        ERROR
    }

    /**
     * The current status
     */
    private BuildContext.Status status = BuildContext.Status.OK;

    /**
     * The source of the component builds, in order
     */
    private List<DirSourceNode> buildOrder = new LinkedList<>();

    /**
     * The root configuration object read from the file (if applicable)
     */
    private Root config;

    /**
     * A map to the build components to look them up by name
     */
    private Map<String, Component> componentMap = new HashMap<>();

    /**
     * A map to the source nodes to look them up by name
     */
    private Map<String, DirSourceNode> nodeMap = new HashMap<>();

    /**
     * A list of post actions that should be executed. Actions
     * have a chance to clean up behind themselves when the
     * build is complete.
     */
    private final List<PostAction> postActions = new LinkedList<>();

    /**
     * The client instance being used to communicate with the OpenShift instance
     */
    private OpenShiftClient client;

    /**
     * The current command structure
     */
    private Command command;

    /**
     * Path to the output artifact that will be uploaded to the build pod
     */
    private Path outputTar;

    /**
     * Path to the configuration file
     */
    private Path configPath;

    /**
     * The namespace that is the target of the current execution
     */
    private String namespace;

    /**
     * The builder image to use. Defaults to latest if none given.
     */
    private String builderImageName = "latest";

    public BuildContext.Status getStatus() {
        return status;
    }

    public BuildContext setStatus(BuildContext.Status status) {
        this.status = status;
        return this;
    }

    public List<DirSourceNode> getBuildOrder() {
        return buildOrder;
    }

    public void setBuildOrder(List<DirSourceNode> buildOrder) {
        this.buildOrder = buildOrder;
    }

    /**
     * Get the command that is being executed if being executed in an
     * openshift targeted context
     *
     * @return the related command that extends OpenShiftCommand, null if not being executed against openshift
     */
    public CommandOpenShift getOpenshiftCommand() {
        // find the actual openshift-related command being executed
        if (this.command != null && this.command.getBuild() != null) {
            return this.command.getBuild();
        } else if (this.command != null && this.command.getBootstrap() != null) {
            return this.command.getBootstrap();
        } else if (this.command != null && this.command.getCache() != null) {
            return this.command.getCache();
        }

        // this would lead to an error
        return null;
    }

    /**
     * Add an action that should be performed once the execution is complete.
     */
    public void addPostAction(final PostAction postAction) {
        this.postActions.add(postAction);
    }

    public List<PostAction> getPostActions() {
        return this.postActions;
    }

    public OpenShiftClient getClient() {
        return client;
    }

    public void setClient(OpenShiftClient client) {
        this.client = client;
    }

    public Command getCommand() {
        return command;
    }

    public void setCommand(Command command) {
        this.command = command;
    }

    public Root getConfig() {
        return config;
    }

    public void setConfig(Root config) {
        this.config = config;
    }

    public Map<String, Component> getComponentMap() {
        return componentMap;
    }

    public void setComponentMap(Map<String, Component> componentMap) {
        this.componentMap = componentMap;
    }

    public Map<String, DirSourceNode> getNodeMap() {
        return nodeMap;
    }

    public void setNodeMap(Map<String, DirSourceNode> nodeMap) {
        this.nodeMap = nodeMap;
    }

    public Path getOutputTar() {
        return outputTar;
    }

    public void setOutputTar(Path outputTar) {
        this.outputTar = outputTar;
    }

    public CommandBuild getCommandBuild() {
        return this.getCommand().getBuild();
    }

    public Path getConfigPath() {
        return configPath;
    }

    public void setConfigPath(Path configPath) {
        this.configPath = configPath;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getBuilderImageName() {
        return builderImageName;
    }

    public void setBuilderImageName(String builderImageName) {
        this.builderImageName = builderImageName;
    }
}
