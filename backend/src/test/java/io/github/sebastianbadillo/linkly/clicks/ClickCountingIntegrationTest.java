package io.github.sebastianbadillo.linkly.clicks;

import io.github.sebastianbadillo.linkly.TestcontainersConfiguration;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyFilter;
import io.github.sebastianbadillo.linkly.apikey.ApiKeyService;
import io.github.sebastianbadillo.linkly.entity.Link;
import io.github.sebastianbadillo.linkly.repository.LinkRepository;
import io.valkey.springframework.data.valkey.core.StringValkeyTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Click counting against a REAL Postgres and a REAL Valkey.
 * The interval is 1 hour so the scheduled job never runs by itself: each test calls flush().
 */
@SpringBootTest(properties = "linkly.clicks.flush-interval=1h")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClickCountingIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ApiKeyService apiKeys;
    @Autowired
    private ClickFlusher flusher;
    @Autowired
    private LinkRepository repository;
    @Autowired
    private StringValkeyTemplate valkey;

    private String apiKey;

    @BeforeEach
    void createApiKey() {
        apiKey = apiKeys.create("test").key();
    }

    @Test
    void hundredRedirectsBecomeHundredClicksAfterOneFlushAndAreNotCountedTwice() throws Exception {
        create("click-100");
        for (int i = 0; i < 100; i++) {
            mvc.perform(get("/click-100")).andExpect(status().isFound());
        }
        assertThat(clicksInPostgres("click-100")).isZero(); // still buffered in Valkey

        flusher.flush();
        assertThat(clicksInPostgres("click-100")).isEqualTo(100);

        flusher.flush();
        assertThat(clicksInPostgres("click-100")).isEqualTo(100);

        mvc.perform(get("/api/links/click-100").header(ApiKeyFilter.HEADER, apiKey))
                .andExpect(jsonPath("$.clicks").value(100));
    }

    @Test
    void unknownCodeIsNotCounted() throws Exception {
        mvc.perform(get("/click-nope")).andExpect(status().isNotFound());

        assertThat(valkey.hasKey("click:click-nope")).isFalse();
    }

    @Test
    void editingAStaleLinkDoesNotOverwriteTheClicks() throws Exception {
        create("click-edit");
        Link stale = repository.findByCode("click-edit").orElseThrow(); // loaded while clicks = 0

        repository.addClicks("click-edit", 5); // a flush happens in the meantime
        stale.setTargetUrl("https://example.com/new");
        repository.saveAndFlush(stale);

        assertThat(clicksInPostgres("click-edit")).isEqualTo(5);
    }

    @Test
    void deletingALinkDiscardsItsPendingClicks() throws Exception {
        create("click-del");
        mvc.perform(get("/click-del")).andExpect(status().isFound());

        mvc.perform(delete("/api/links/click-del").header(ApiKeyFilter.HEADER, apiKey))
                .andExpect(status().isNoContent());
        create("click-del"); // same alias, new link
        flusher.flush();

        assertThat(clicksInPostgres("click-del")).isZero();
    }

    private long clicksInPostgres(String code) {
        return repository.findByCode(code).orElseThrow().getClicks();
    }

    private void create(String alias) throws Exception {
        mvc.perform(post("/api/links").header(ApiKeyFilter.HEADER, apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/a", "alias": "%s"}""".formatted(alias)))
                .andExpect(status().isCreated());
    }
}
