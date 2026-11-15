package io.github.sebastianbadillo.linkly.exception;

/** The target URL broke one of the URL rules (empty, too long, not http/https...). Mapped to HTTP 400. */
public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
