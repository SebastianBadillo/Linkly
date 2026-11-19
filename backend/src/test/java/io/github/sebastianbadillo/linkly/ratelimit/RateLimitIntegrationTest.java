package io.github.sebastianbadillo.linkly.ratelimit;

import io.github.sebastianbadillo.linkly.TestcontainersConfiguration;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyFilter;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyRepository;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole chain against REAL Postgres + Valkey: admin token, API keys, and the Lua token bucket.
 * refill-seconds is 1 here (instead of 6) so the "wait for a refill" test is fast.
 */
@SpringBootTest(properties = {"linkly.admin-token=test-admin", "linkly.rate-limit.refill-seconds=1"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApiKeyService apiKeys;
    @Autowired
    private ApiKeyRepository repository;
    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void adminCreatesAKeyAndTheKeyWorks() throws Exception {
        String body = mvc.perform(post("/api/keys")
                        .header("X-Admin-Token", "test-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "it"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.key").value(startsWith("lk_")))
                .andReturn().getResponse().getContentAsString();
        String key = body.replaceAll(".*\"key\":\"([^\"]+)\".*", "$1");

        mvc.perform(get("/api/links").header(ApiKeyFilter.HEADER, key)).andExpect(status().isOk());
    }

    @Test
    void noKeyOrRevokedKeyIs401ButRedirectsStayPublic() throws Exception {
        mvc.perform(get("/api/links")).andExpect(status().isUnauthorized());

        var created = apiKeys.create("to revoke");
        var entity = repository.findById(created.id()).orElseThrow();
        entity.revoke();
        repository.save(entity);
        mvc.perform(get("/api/links").header(ApiKeyFilter.HEADER, created.key()))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/no-such-code")).andExpect(status().isNotFound()); // 404, not 401
    }

    @Test
    void tenPassThenThe11thIs429AndARefillLetsRequestsThroughAgain() throws Exception {
        String key = apiKeys.create("burst").key();

        for (int i = 0; i < 10; i++) {
            mvc.perform(get("/api/links").header(ApiKeyFilter.HEADER, key)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/links").header(ApiKeyFilter.HEADER, key))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.error").value("rate limit exceeded"));

        Thread.sleep(1_100); // 1 token per second in this test

        mvc.perform(get("/api/links").header(ApiKeyFilter.HEADER, key)).andExpect(status().isOk());
    }

    @Test
    void twentyParallelRequestsOnAFullBucketAllowExactlyTen() throws Exception {
        long bucketId = 900_000L; // not a real key: we call the limiter directly, only the bucket matters
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1); // all threads fire at the same moment

        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Boolean> call = () -> {
                start.await();
                return rateLimiter.tryConsume(bucketId).allowed();
            };
            results.add(pool.submit(call));
        }
        start.countDown();

        int allowed = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                allowed++;
            }
        }
        pool.shutdown();

        // Without atomicity, two threads could both read "1 token" and both pass -> more than 10.
        // (At most 1 token refills during the test, so we also accept 11.)
        assertThat(allowed).isBetween(10, 11);
    }
}
