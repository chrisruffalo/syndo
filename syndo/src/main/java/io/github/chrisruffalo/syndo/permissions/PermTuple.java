package io.github.chrisruffalo.syndo.permissions;

public class PermTuple {
    private final String verb;
    private final String resource;

    public PermTuple(final String verb, final String resource) {
        this.verb = verb;
        this.resource = resource;
    }

    public String getVerb() {
        return verb;
    }

    public String getResource() {
        return resource;
    }
}
