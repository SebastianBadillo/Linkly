package io.github.sebastianbadillo.linkly.exception;

/**
 * Every generated code collided. The client did nothing wrong, so this is OUR fault: HTTP 500.
 * Practically impossible with 2.2 billion codes; if it happens, the table is nearly full or the generator is broken.
 */
public class CodeGenerationException extends RuntimeException {

    public CodeGenerationException(String message) {
        super(message);
    }
}
