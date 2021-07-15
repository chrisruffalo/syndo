package io.github.chrisruffalo.syndo.executions.actions.impl;

import io.fabric8.openshift.api.model.ImageStreamTag;
import io.fabric8.openshift.client.OpenShiftClient;
import io.github.chrisruffalo.syndo.executions.actions.BaseAction;
import io.github.chrisruffalo.syndo.executions.actions.BuildContext;
import io.github.chrisruffalo.syndo.model.DirSourceNode;

import java.util.LinkedList;
import java.util.List;

public class HashFilterAction extends BaseAction {

    @Override
    public void build(BuildContext context) {
        final OpenShiftClient client = context.getClient();
        final List<DirSourceNode> dirSourceNodeList = new LinkedList<>();

        // go through each dir source node and look for an existing output if a hash is present
        // if the hash is present AND no image tag exists with that hash, add the node. if
        // an image tag exists with the hash already, do not add the node
        for (final DirSourceNode node : context.getBuildOrder()) {
            String outputRef = node.getFullOutputRef();
            if (outputRef.contains(":")) {
                if (outputRef.contains("/") && !outputRef.endsWith("/")) {
                    outputRef = outputRef.substring(outputRef.lastIndexOf("/")+1);
                }
                final ImageStreamTag ist = client.imageStreamTags().inNamespace(context.getNamespace()).withName(outputRef).get();
                if (ist != null) {
                    logger().info("Found image tag matching component {} content at {}/{}", node.getName(), context.getNamespace(), outputRef);
                    continue;
                }
            } else {
                logger().info("no tag in image ref {} to use", outputRef);
            }
            dirSourceNodeList.add(node);
        }

        context.setBuildOrder(dirSourceNodeList);
        if (dirSourceNodeList.isEmpty()) {
            logger().info("All components have image stream tags matching their content");
            context.setStatus(BuildContext.Status.DONE);
        }
    }
}
