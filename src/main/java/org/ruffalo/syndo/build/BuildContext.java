package org.ruffalo.syndo.build;

import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.cmd.CommandBuild;
import org.ruffalo.syndo.config.Component;
import org.ruffalo.syndo.config.Root;
import org.ruffalo.syndo.model.DirSourceNode;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BuildContext {

    public enum Status {
        OK,
        DONE,
        INVALID,
        ERROR
    }

    private BuildContext.Status status = BuildContext.Status.OK;

    private List<DirSourceNode> buildOrder = new LinkedList<>();

    private Root config;

    private Map<String, Component> componentMap = new HashMap<>();

    private Map<String, DirSourceNode> nodeMap = new HashMap<>();

    private OpenShiftClient client;

    private Command command;

    private CommandBuild commandBuild;

    private Path outputTar;

    private Path configPath;

    private String namespace;

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
        return commandBuild;
    }

    public void setCommandBuild(CommandBuild commandBuild) {
        this.commandBuild = commandBuild;
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
