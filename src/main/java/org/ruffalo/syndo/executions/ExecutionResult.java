package org.ruffalo.syndo.executions;

public class ExecutionResult {

    private String message;

    private int exitCode = 0;

    public ExecutionResult() {
        this(0, "");
    }

    public ExecutionResult(final int exitCode) {
        this(exitCode, "");
    }

    public ExecutionResult(final int exitCode, final String message) {
        this.exitCode = exitCode;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }
}
