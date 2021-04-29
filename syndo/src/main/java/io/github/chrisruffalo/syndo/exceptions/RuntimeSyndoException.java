package io.github.chrisruffalo.syndo.exceptions;

public class RuntimeSyndoException extends RuntimeException implements SyndoError {

    public RuntimeSyndoException() {
        super();
    }

    public RuntimeSyndoException(String message) {
        super(message);
    }

    public RuntimeSyndoException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeSyndoException(Throwable cause) {
        super(cause);
    }

    protected RuntimeSyndoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
