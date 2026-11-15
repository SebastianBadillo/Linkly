package io.github.sebastianbadillo.linkly.exception;

/** No link has this code. Mapped to HTTP 404. */
public class LinkNotFoundException extends RuntimeException {

    public LinkNotFoundException(String code) {
        super("No link with code '%s'".formatted(code));
    }
}
