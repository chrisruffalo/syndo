package org.ruffalo.syndo.build;

import io.fabric8.openshift.client.OpenShiftClient;
import org.ruffalo.syndo.cmd.Command;
import org.ruffalo.syndo.config.Component;
import org.ruffalo.syndo.config.Root;
import org.ruffalo.syndo.model.DirSourceNode;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BuildContext {

    public enum Status {
        OK,
        ERROR
    }

    private BuildContext.Status status = BuildContext.Status.OK;

    private List<DirSourceNode> buildOrder = new LinkedList<>();

    private Root config;

    private Map<String, Component> componentMap = new HashMap<>();

    private Map<String, DirSourceNode> nodeMap = new HashMap<>();

    private OpenShiftClient client;

    private Command command;

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
}
