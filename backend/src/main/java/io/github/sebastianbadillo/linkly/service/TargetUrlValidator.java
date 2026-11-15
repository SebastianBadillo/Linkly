package io.github.sebastianbadillo.linkly.service;

import io.github.sebastianbadillo.linkly.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Rules for the URL a link points to. Returns the trimmed URL or throws.
 * Only http/https: a link to "javascript:alert(1)" would run code in the victim's browser.
 */
@Component
public class TargetUrlValidator {

    static final int MAX_LENGTH = 2048; // same limit as the DB CHECK constraint
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    public String validate(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidUrlException("URL must not be empty");
        }
        String trimmed = url.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidUrlException("URL must be at most %d characters".formatted(MAX_LENGTH));
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidUrlException("URL is not well formed");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new InvalidUrlException("URL must start with http:// or https://");
        }
        if (uri.getHost() == null) {
            throw new InvalidUrlException("URL must have a host, e.g. https://example.com");
        }
        return trimmed;
    }
}
