package org.ruffalo.syndo.model;

import java.util.HashSet;
import java.util.Set;

public abstract class BuildNode {

    private final Set<BuildNode> dependsOn = new HashSet<>();

    public BuildNode() {

    }

    /**
     * A set of build nodes that this node requires in order to
     * build.
     *
     * @return a set of build nodes that this node depends on
     */
    public abstract BuildNode from();

}
