package org.ruffalo.syndo.config;

import java.util.LinkedList;
import java.util.List;

/**
 * An alias serves as a shortcut or name for a list of components. In the event that
 * a common build has three components "ui, service, db" you might assign that to an
 * alias "stack" so that the stack can be built in one option.
 */
public class Alias {

    private String name;

    private List<String> components = new LinkedList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getComponents() {
        return components;
    }

    public void setComponents(List<String> components) {
        this.components = components;
    }
}
