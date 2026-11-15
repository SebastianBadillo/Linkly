package io.github.sebastianbadillo.linkly.dto;

/** Body of PATCH /api/links/{code}. Only the target URL can change; the code never does. */
public record UpdateLinkRequest(String url) {
}
