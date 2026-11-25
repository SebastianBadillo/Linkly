package io.github.sebastianbadillo.linkly.dto;

import io.github.sebastianbadillo.linkly.entity.Link;

import java.time.Instant;

/**
 * What the API sends back for a link. We never return the entity itself:
 * the API shape stays stable even if the table changes (e.g. the owner column in M2 stays private).
 */
public record LinkResponse(String code, String shortUrl, String targetUrl, Instant createdAt, long clicks) {

    public static LinkResponse from(Link link, String baseUrl) {
        return new LinkResponse(
                link.getCode(),
                baseUrl + "/" + link.getCode(),
                link.getTargetUrl(),
                link.getCreatedAt(),
                link.getClicks());
    }
}
