package io.github.sebastianbadillo.linkly.apikey;

import io.github.sebastianbadillo.linkly.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit test: no Spring. Fake request/response objects + mocked service and limiter. */
class ApiKeyFilterTest {

    private final ApiKeyService apiKeys = mock(ApiKeyService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final ApiKeyFilter filter = new ApiKeyFilter(apiKeys, rateLimiter);

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @Test
    void missingOrUnknownOrRevokedKeyIs401AndStopsTheChain() throws Exception {
        // authenticate() returns empty for all three cases (tested in ApiKeyServiceTest).
        when(apiKeys.authenticate(any())).thenReturn(Optional.empty());

        filter.doFilter(request("GET", "/api/links", "lk_wrong"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"missing or invalid API key\"}");
        assertThat(chain.getRequest()).isNull(); // the controller was never reached
        verify(rateLimiter, never()).tryConsume(anyLong()); // no bucket for strangers
    }

    @Test
    void validKeyWithTokensPassesThrough() throws Exception {
        ApiKey key = keyWithId(7L);
        when(apiKeys.authenticate("lk_good")).thenReturn(Optional.of(key));
        when(rateLimiter.tryConsume(7L)).thenReturn(RateLimiter.Decision.ALLOW);

        MockHttpServletRequest request = request("GET", "/api/links", "lk_good");
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(request.getAttribute(ApiKeyFilter.ATTRIBUTE)).isSameAs(key);
    }

    @Test
    void emptyBucketIs429WithRetryAfter() throws Exception {
        // Build the mock BEFORE when(...): stubbing inside another stubbing confuses Mockito.
        ApiKey key = keyWithId(7L);
        when(apiKeys.authenticate("lk_good")).thenReturn(Optional.of(key));
        when(rateLimiter.tryConsume(7L)).thenReturn(new RateLimiter.Decision(false, 2));

        filter.doFilter(request("POST", "/api/links", "lk_good"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("2");
        assertThat(response.getContentAsString()).isEqualTo("{\"error\":\"rate limit exceeded\"}");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void creatingAKeyIsSkippedBecauseTheAdminTokenProtectsIt() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/keys", null);

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verify(apiKeys, never()).authenticate(any());
    }

    private static MockHttpServletRequest request(String method, String uri, String apiKey) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (apiKey != null) {
            request.addHeader(ApiKeyFilter.HEADER, apiKey);
        }
        return request;
    }

    private static ApiKey keyWithId(long id) {
        ApiKey key = mock(ApiKey.class);
        when(key.getId()).thenReturn(id);
        return key;
    }
}
