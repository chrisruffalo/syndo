package org.ruffalo.syndo.model;

import java.util.HashSet;
import java.util.Set;

public abstract class BuildNode {

    private final Set<BuildNode> dependsOn = new HashSet<>();

    public BuildNode() {

    }

}
