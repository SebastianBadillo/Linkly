package io.github.sebastianbadillo.linkly.cache;

import io.github.sebastianbadillo.linkly.TestcontainersConfiguration;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyFilter;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole app against a REAL Postgres and a REAL Valkey (both in Docker).
 * We read Valkey directly to prove what the cache holds, not just what the API answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LinkCacheIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApiKeyService apiKeys;

    private String apiKey;

    // A fresh key per test = a fresh, full rate-limit bucket per test.
    @BeforeEach
    void createApiKey() {
        apiKey = apiKeys.create("test").key();
    }
    @Autowired
    private StringValkeyTemplate valkey;

    @Test
    void redirectStoresTheUrlWithAOneHourTtl() throws Exception {
        create("cache-ttl", "https://example.com/a");

        mvc.perform(get("/cache-ttl")).andExpect(status().isFound());

        assertThat(valkey.opsForValue().get("link:cache-ttl")).isEqualTo("https://example.com/a");
        Long ttlSeconds = valkey.getExpire("link:cache-ttl");
        assertThat(ttlSeconds).isBetween(3500L, 3600L); // ~1 h, minus a few seconds of test time
    }

    @Test
    void updateEvictsSoTheNextRedirectFollowsTheNewUrl() throws Exception {
        create("cache-upd", "https://example.com/old");
        mvc.perform(get("/cache-upd")).andExpect(header().string("Location", "https://example.com/old"));

        mvc.perform(patch("/api/links/cache-upd").header(ApiKeyFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/new"}"""))
                .andExpect(status().isOk());

        assertThat(valkey.hasKey("link:cache-upd")).isFalse(); // evicted
        mvc.perform(get("/cache-upd")).andExpect(header().string("Location", "https://example.com/new"));
    }

    @Test
    void notFoundMarkerIsReplacedWhenTheLinkIsCreated() throws Exception {
        mvc.perform(get("/cache-later")).andExpect(status().isNotFound());
        assertThat(valkey.opsForValue().get("link:cache-later")).isEqualTo(LinkCache.NOT_FOUND);
        assertThat(valkey.getExpire("link:cache-later")).isBetween(1L, 60L); // the SHORT ttl

        create("cache-later", "https://example.com/now-exists");

        mvc.perform(get("/cache-later"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/now-exists"));
    }

    private void create(String alias, String url) throws Exception {
        mvc.perform(post("/api/links").header(ApiKeyFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "alias": "%s"}""".formatted(url, alias)))
                .andExpect(status().isCreated());
    }
}
