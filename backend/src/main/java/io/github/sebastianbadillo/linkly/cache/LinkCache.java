package io.github.sebastianbadillo.linkly.cache;

import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * The cache side of cache-aside: code -> target URL, stored in Valkey.
 *
 * Valkey is only a speed-up, never the source of truth. So every method here swallows Valkey
 * errors (down, timeout...) and logs a warning: the caller then simply uses Postgres.
 * We catch RuntimeException on purpose: ANY cache problem must never break a redirect.
 */
@Slf4j
@Component
public class LinkCache {

    /** Stored for codes that don't exist, so repeated bad codes don't hit Postgres ("negative caching"). */
    public static final String NOT_FOUND = "__NOT_FOUND__";

    private static final String KEY_PREFIX = "link:";

    private final StringValkeyTemplate valkey;
    private final Duration ttl;
    private final Duration notFoundTtl;

    public LinkCache(StringValkeyTemplate valkey,
                     @Value("${linkly.cache.ttl}") Duration ttl,
                     @Value("${linkly.cache.not-found-ttl}") Duration notFoundTtl) {
        this.valkey = valkey;
        this.ttl = ttl;
        this.notFoundTtl = notFoundTtl;
    }

    /** The cached value (a URL or {@link #NOT_FOUND}), or empty on a miss OR when Valkey fails. */
    public Optional<String> get(String code) {
        try {
            return Optional.ofNullable(valkey.opsForValue().get(key(code)));
        } catch (RuntimeException e) {
            log.warn("Valkey GET failed for '{}', falling back to Postgres: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    public void putUrl(String code, String targetUrl) {
        set(code, targetUrl, ttl);
    }

    public void putNotFound(String code) {
        set(code, NOT_FOUND, notFoundTtl);
    }

    /** Called AFTER the Postgres write. If it fails, the TTL removes the stale value later. */
    public void evict(String code) {
        try {
            valkey.delete(key(code));
        } catch (RuntimeException e) {
            log.warn("Valkey DEL failed for '{}', stale value lives until its TTL: {}", code, e.getMessage());
        }
    }

    private void set(String code, String value, Duration timeToLive) {
        try {
            valkey.opsForValue().set(key(code), value, timeToLive); // SET key value EX seconds
        } catch (RuntimeException e) {
            log.warn("Valkey SET failed for '{}' (redirect still works): {}", code, e.getMessage());
        }
    }

    static String key(String code) {
        return KEY_PREFIX + code;
    }
}
