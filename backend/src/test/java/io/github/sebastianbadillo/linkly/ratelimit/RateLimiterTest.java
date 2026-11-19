package io.github.sebastianbadillo.linkly.ratelimit;

import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Java side only: reading the script result, Retry-After math, fail-open. The Lua runs in the IT. */
class RateLimiterTest {

    private final StringValkeyTemplate valkey = mock(StringValkeyTemplate.class);
    private final RateLimiter limiter = new RateLimiter(valkey, 10, 6);

    @Test
    void scriptSaysYes() {
        when(valkey.execute(any(), anyList(), any(Object[].class))).thenReturn(List.of(1L, "8.0"));

        assertThat(limiter.tryConsume(1L)).isEqualTo(RateLimiter.Decision.ALLOW);
    }

    @Test
    void scriptSaysNoWithRetryAfterFromTheSpecExample() {
        // 0.67 tokens left -> (1 - 0.67) * 6 = 1.98 -> 2 seconds
        when(valkey.execute(any(), anyList(), any(Object[].class))).thenReturn(List.of(0L, "0.67"));

        assertThat(limiter.tryConsume(1L)).isEqualTo(new RateLimiter.Decision(false, 2));
    }

    @Test
    void retryAfterIsNeverZero() {
        assertThat(limiter.retryAfterSeconds(0.999)).isEqualTo(1);
        assertThat(limiter.retryAfterSeconds(0.0)).isEqualTo(6);
    }

    @Test
    void valkeyDownFailsOpen() {
        when(valkey.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new ValkeyConnectionFailureException("connection refused"));

        assertThat(limiter.tryConsume(1L).allowed()).isTrue();
    }
}
