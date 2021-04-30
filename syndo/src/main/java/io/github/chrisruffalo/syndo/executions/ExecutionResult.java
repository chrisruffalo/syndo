package io.github.chrisruffalo.syndo.executions;

public class ExecutionResult {

    private Throwable throwable;

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

    public ExecutionResult(final int exitCode, final Throwable throwable) {
        this(exitCode, throwable.getMessage(), throwable);
    }

    public ExecutionResult(final int exitCode, final String message, final Throwable throwable) {
        this(exitCode, message);
        this.throwable = throwable;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }
}
