package io.github.sebastianbadillo.linkly.dto;

/** Body of POST /api/links. alias is optional: null means "generate a code for me". */
public record CreateLinkRequest(String url, String alias) {
}
