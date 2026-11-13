package io.github.sebastianbadillo.linkly.service;

/**
 * Produces candidate short codes. Uniqueness is NOT guaranteed here:
 * the database unique constraint decides, and the caller retries on collision.
 */
public interface ShortCodeGenerator {

    String generate();
}
