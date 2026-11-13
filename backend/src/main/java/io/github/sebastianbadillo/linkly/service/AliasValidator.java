package io.github.sebastianbadillo.linkly.service;

import io.github.sebastianbadillo.linkly.exception.InvalidAliasException;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rules for custom aliases. Returns the normalized (lowercase) alias or throws.
 * Whether the alias is already taken is NOT checked here: the DB unique constraint does that.
 */
@Component
public class AliasValidator {

    static final int MIN_LENGTH = 3;
    static final int MAX_LENGTH = 30;

    // Only URL-safe characters: no '/', '?', '#', spaces or Unicode look-alikes.
    private static final Pattern ALLOWED = Pattern.compile("[a-z0-9_-]+");

    // Our own routes, plus words that would help phishing (e.g. linkly.app/login).
    static final Set<String> RESERVED = Set.of(
            "api", "actuator", "swagger", "swagger-ui", "health",
            "admin", "login", "logout", "account", "dashboard");

    public String normalize(String alias) {
        if (alias == null || alias.isBlank()) {
            throw new InvalidAliasException("Alias must not be empty");
        }
        // Locale.ROOT: in some languages (e.g. Turkish) "I".toLowerCase() is not "i".
        String normalized = alias.strip().toLowerCase(Locale.ROOT);

        if (normalized.length() < MIN_LENGTH || normalized.length() > MAX_LENGTH) {
            throw new InvalidAliasException(
                    "Alias must be between %d and %d characters".formatted(MIN_LENGTH, MAX_LENGTH));
        }
        if (!ALLOWED.matcher(normalized).matches()) {
            throw new InvalidAliasException("Alias may only contain letters, digits, '-' and '_'");
        }
        if (RESERVED.contains(normalized)) {
            throw new InvalidAliasException("Alias '%s' is reserved".formatted(normalized));
        }
        return normalized;
    }
}
