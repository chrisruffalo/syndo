package io.github.chrisruffalo.syndo.exceptions;

public class SyndoException extends Exception implements SyndoError {

    public SyndoException() {
        super();
    }

    public SyndoException(String message) {
        super(message);
    }

    public SyndoException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyndoException(Throwable cause) {
        super(cause);
    }

}
