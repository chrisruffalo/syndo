package org.ruffalo.syndo.build;

import org.ruffalo.syndo.config.Alias;
import org.ruffalo.syndo.model.BuildNode;
import org.ruffalo.syndo.model.DirSourceNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ComponentFilterAction extends BaseAction {

    @Override
    public void build(BuildContext context) {
        // at this point we have a list of all the nodes in build order
        // and we should be able to filter them out based on what components are selected
        final List<DirSourceNode> nodes = context.getBuildOrder();

        // start with a list of components
        final Set<String> givenComponents = new HashSet<>(context.getCommandBuild().getComponents());

        // quit early if "all" is in given components
        if (givenComponents == null || givenComponents.isEmpty() || givenComponents.contains("all")) {
            return;
        }

        final List<DirSourceNode> newOrder = new LinkedList<>();
        final Set<String> resolvedComponents = new HashSet<>();

        // create alias name map
        final Map<String, Alias> aliasMap = new HashMap<>();
        context.getConfig().getAliases().forEach(alias -> aliasMap.put(alias.getName(), alias));

        // resolve aliases from component list
        for(final String component : givenComponents) {
            final Alias alias = aliasMap.get(component);
            if (alias == null) {
                resolvedComponents.add(component);
            } else {
                resolvedComponents.addAll(alias.getComponents());
            }
        }

        // record removed components
        final Set<String> removed = new HashSet<>();

        // walk through the build and include components that were requested
        for(final DirSourceNode node : nodes) {
            if(resolvedComponents.contains(node.getName())) {
                final List<DirSourceNode> reAdded = new LinkedList<>();
                BuildNode parent = node.getFrom();
                while (parent instanceof DirSourceNode) {
                    final DirSourceNode dsNode = (DirSourceNode)parent;
                    if(removed.contains(dsNode.getName())) {
                        logger().info("Selected component {} depends on unselected component {}", node.getName(), dsNode.getName());
                        reAdded.add(0, dsNode);
                    }
                    parent = dsNode.getFrom();
                }
                newOrder.addAll(reAdded);
                newOrder.add(node);
                continue;
            }
            removed.add(node.getName());
        }

        // update the build list with the new order
        context.setBuildOrder(newOrder);
    }
}
