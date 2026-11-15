package io.github.sebastianbadillo.linkly.exception;

/** A custom alias broke one of the alias rules. Mapped to HTTP 400 by ApiExceptionHandler. */
public class InvalidAliasException extends RuntimeException {

    public InvalidAliasException(String message) {
        super(message);
    }
}
