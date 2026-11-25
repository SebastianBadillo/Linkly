package io.github.sebastianbadillo.linkly.clicks;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * The clicks that are waiting in Valkey to be written to Postgres:
 * <ul>
 *   <li>{@code click:{code}} - one counter per link</li>
 *   <li>{@code clicks:pending} - the flush job's to-do list: codes that have a counter</li>
 * </ul>
 * {@link #record} and {@link #discard} run during user requests, so they swallow Valkey errors.
 * The methods used by the flush let errors through: {@link ClickFlusher} decides what to do.
 */
@Slf4j
@Component
public class ClickCounter {

    static final String PENDING_KEY = "clicks:pending";
    private static final String KEY_PREFIX = "click:";

    private final StringValkeyTemplate valkey;
    private final Counter failedClicks;

    public ClickCounter(StringValkeyTemplate valkey, MeterRegistry meters) {
        this.valkey = valkey;
        this.failedClicks = Counter.builder("linkly.clicks.failed")
                .description("Redirects whose click could not be recorded in Valkey")
                .register(meters);
    }

    /** Called on every successful redirect. Must never break it. */
    public void record(String code) {
        String normalized = code.toLowerCase(Locale.ROOT); // BF26 and bf26 are the same link
        try {
            add(normalized, 1);
        } catch (RuntimeException e) {
            // A metric, not a warning: during an outage this happens on EVERY request.
            failedClicks.increment();
            log.debug("Could not record a click for '{}': {}", normalized, e.getMessage());
        }
    }

    /** Removes and returns up to {@code max} codes from the to-do list (SPOP: atomic). */
    List<String> takePendingCodes(int max) {
        return valkey.opsForSet().pop(PENDING_KEY, max);
    }

    /** Reads the counter and deletes it in one atomic step (GETDEL). 0 if there is no counter. */
    long takeCount(String code) {
        String value = valkey.opsForValue().getAndDelete(key(code));
        return value == null ? 0 : Long.parseLong(value);
    }

    /** Gives clicks back after a failed database write, so the next flush retries them. */
    void restore(String code, long count) {
        add(code, count);
    }

    /** For deleted links: a new link with the same alias must not inherit these clicks. */
    public void discard(String code) {
        try {
            valkey.opsForSet().remove(PENDING_KEY, code);
            valkey.delete(key(code));
        } catch (RuntimeException e) {
            log.warn("Could not discard pending clicks for '{}': {}", code, e.getMessage());
        }
    }

    // INCRBY (adds, never overwrites) and only THEN SADD, so a listed code already has its counter.
    // The other order would let a flush take the code before the counter exists.
    private void add(String code, long amount) {
        valkey.opsForValue().increment(key(code), amount);
        valkey.opsForSet().add(PENDING_KEY, code);
    }

    static String key(String code) {
        return KEY_PREFIX + code;
    }
}
