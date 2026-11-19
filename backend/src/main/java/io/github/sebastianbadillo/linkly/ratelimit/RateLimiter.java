package io.github.sebastianbadillo.linkly.ratelimit;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.core.script.ValkeyScript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Per-API-key token bucket. The math lives in scripts/token_bucket.lua so it runs atomically in Valkey:
 * with 3 app instances, two of them can never both take the last token.
 */
@Slf4j
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "ratelimit:";

    @SuppressWarnings("rawtypes")
    private static final ValkeyScript<List> SCRIPT =
            ValkeyScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final StringValkeyTemplate valkey;
    private final int capacity;
    private final double refillSeconds;

    public RateLimiter(StringValkeyTemplate valkey,
                       @Value("${linkly.rate-limit.capacity}") int capacity,
                       @Value("${linkly.rate-limit.refill-seconds}") double refillSeconds) {
        this.valkey = valkey;
        this.capacity = capacity;
        this.refillSeconds = refillSeconds;
    }

    /** allowed = the request may pass; retryAfterSeconds = wait until 1 token is back (0 when allowed). */
    public record Decision(boolean allowed, long retryAfterSeconds) {
        public static final Decision ALLOW = new Decision(true, 0);
    }

    public Decision tryConsume(long keyId) {
        try {
            List<?> result = valkey.execute(SCRIPT, List.of(KEY_PREFIX + keyId),
                    String.valueOf(capacity), String.valueOf(refillSeconds));
            boolean allowed = ((Number) result.get(0)).longValue() == 1;
            if (allowed) {
                return Decision.ALLOW;
            }
            double tokens = Double.parseDouble(result.get(1).toString());
            return new Decision(false, retryAfterSeconds(tokens));
        } catch (RuntimeException e) {
            // Fail OPEN: Valkey down must not take the API down. Trade-off: no limit during an outage.
            log.warn("Rate limiter unavailable for key {}, allowing the request: {}", keyId, e.getMessage());
            return Decision.ALLOW;
        }
    }

    /** Worked example: 0.67 tokens, 6 s per token -> (1 - 0.67) * 6 = 1.98 -> 2 s. Never less than 1. */
    long retryAfterSeconds(double tokens) {
        return Math.max(1, (long) Math.ceil((1 - tokens) * refillSeconds));
    }
}
