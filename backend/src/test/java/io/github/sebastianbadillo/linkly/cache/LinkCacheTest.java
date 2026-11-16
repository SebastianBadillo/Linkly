package io.github.sebastianbadillo.linkly.cache;

import io.valkey.springframework.data.valkey.ValkeyConnectionFailureException;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.valkey.springframework.data.valkey.core.ValueOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests: the Valkey template is a mock, so we can make "Valkey is down" happen on demand. */
@ExtendWith(MockitoExtension.class)
class LinkCacheTest {

    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration NOT_FOUND_TTL = Duration.ofSeconds(60);

    @Mock
    private StringValkeyTemplate valkey;
    @Mock
    private ValueOperations<String, String> ops;

    private LinkCache cache;

    @BeforeEach
    void setUp() {
        cache = new LinkCache(valkey, TTL, NOT_FOUND_TTL);
    }

    @Test
    void getReadsThePrefixedKey() {
        when(valkey.opsForValue()).thenReturn(ops);
        when(ops.get("link:bf26")).thenReturn("https://example.com");

        assertThat(cache.get("bf26")).contains("https://example.com");
    }

    @Test
    void putUrlUsesTheLongTtlAndNotFoundUsesTheShortOne() {
        when(valkey.opsForValue()).thenReturn(ops);

        cache.putUrl("bf26", "https://example.com");
        cache.putNotFound("nope42");

        verify(ops).set("link:bf26", "https://example.com", TTL);
        verify(ops).set("link:nope42", LinkCache.NOT_FOUND, NOT_FOUND_TTL);
    }

    @Test
    void valkeyDownOnGetIsTreatedAsAMiss() {
        when(valkey.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenThrow(new ValkeyConnectionFailureException("connection refused"));

        assertThat(cache.get("bf26")).isEmpty(); // -> the service falls back to Postgres
    }

    @Test
    void valkeyDownOnPutOrEvictNeverThrows() {
        when(valkey.opsForValue()).thenReturn(ops);
        // set(...) returns void, so the "throw" is written the other way round: doThrow(...).when(mock)
        doThrow(new ValkeyConnectionFailureException("down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));
        when(valkey.delete(anyString())).thenThrow(new ValkeyConnectionFailureException("down"));

        assertThatCode(() -> {
            cache.putUrl("bf26", "https://example.com");
            cache.evict("bf26");
        }).doesNotThrowAnyException();
    }
}
