package org.ruffalo.syndo.config;

import java.util.LinkedList;
import java.util.List;

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
