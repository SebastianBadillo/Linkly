package io.github.sebastianbadillo.linkly.exception;

/** A valid custom alias that another link already uses. Mapped to HTTP 409 (conflict with current state). */
public class AliasTakenException extends RuntimeException {

    public AliasTakenException(String alias) {
        super("Alias '%s' is already taken".formatted(alias));
    }
}
