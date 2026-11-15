package io.github.sebastianbadillo.linkly;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: the whole app (controllers, service, real transactions) against a real PostgreSQL in Docker.
 * The unit tests FAKE the duplicate-key error; here the real unique constraint has to produce it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LinkApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void createRedirectUpdateDelete() throws Exception {
        // create
        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/black-friday", "alias": "E2E-Flow"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("e2e-flow")) // alias was lowercased
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // redirect (uppercase in the URL still works)
        mvc.perform(get("/E2E-FLOW"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/black-friday"));

        // update, then the redirect follows the new target (proves dirty checking really wrote to the DB)
        mvc.perform(patch("/api/links/e2e-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/cyber-monday"}"""))
                .andExpect(status().isOk());
        mvc.perform(get("/e2e-flow"))
                .andExpect(header().string("Location", "https://example.com/cyber-monday"));

        // delete, then it is gone
        mvc.perform(delete("/api/links/e2e-flow"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/e2e-flow"))
                .andExpect(status().isNotFound());
    }

    @Test
    void realDuplicateAliasReturns409() throws Exception {
        String body = """
                {"url": "https://example.com", "alias": "e2e-dup"}""";

        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void generatedCodeLinkIsCreatedAndListIsClamped() throws Exception {
        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/generated"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("[a-z0-9]{6}")));

        mvc.perform(get("/api/links").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100)); // clamped
    }
}
