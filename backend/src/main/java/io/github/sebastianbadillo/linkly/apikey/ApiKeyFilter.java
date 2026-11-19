package io.github.sebastianbadillo.linkly.apikey;

import io.github.sebastianbadillo.linkly.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Runs before every /api/** request (registered in ApiSecurityConfig):
 * 1. Who are you?  X-API-Key missing, unknown or revoked -> 401
 * 2. Too fast?     bucket empty -> 429 + Retry-After
 * Redirects (GET /{code}) never reach this filter: they stay public and unlimited.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    /** The caller's ApiKey, for controllers (needed later for link owners). */
    public static final String ATTRIBUTE = "linkly.apiKey";

    private final ApiKeyService apiKeys;
    private final RateLimiter rateLimiter;

    public ApiKeyFilter(ApiKeyService apiKeys, RateLimiter rateLimiter) {
        this.apiKeys = apiKeys;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Issuing keys is protected by the admin token instead (see ApiKeyController).
        return "POST".equals(request.getMethod()) && "/api/keys".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<ApiKey> key = apiKeys.authenticate(request.getHeader(HEADER));
        if (key.isEmpty()) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "missing or invalid API key");
            return;
        }

        RateLimiter.Decision decision = rateLimiter.tryConsume(key.get().getId());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            writeError(response, 429, "rate limit exceeded");
            return;
        }

        request.setAttribute(ATTRIBUTE, key.get());
        chain.doFilter(request, response);
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}"); // fixed messages only, never user input
    }
}
