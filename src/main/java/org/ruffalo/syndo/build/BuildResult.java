package org.ruffalo.syndo.build;

public class BuildResult {

    public enum Status {
        COMPLETE,
        FAILED
    }

    private Status status = Status.COMPLETE;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
