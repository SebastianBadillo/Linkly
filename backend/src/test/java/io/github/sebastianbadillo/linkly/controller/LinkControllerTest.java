package io.github.sebastianbadillo.linkly.controller;

import io.github.sebastianbadillo.linkly.entity.Link;
import io.github.sebastianbadillo.linkly.exception.AliasTakenException;
import io.github.sebastianbadillo.linkly.exception.CodeGenerationException;
import io.github.sebastianbadillo.linkly.exception.InvalidUrlException;
import io.github.sebastianbadillo.linkly.exception.LinkNotFoundException;
import io.github.sebastianbadillo.linkly.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer only (@WebMvcTest): real Spring MVC + JSON + ApiExceptionHandler, but the service is a mock.
 * Tests the HTTP contract (status codes, headers, JSON shape). Business rules are tested in LinkServiceTest.
 */
@WebMvcTest(LinkController.class)
class LinkControllerTest {

    private static final String URL = "https://example.com/black-friday";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkService service;

    private static Link link(String code, String url) {
        Link link = new Link(code, url);
        // createdAt is normally set by Hibernate on insert; there is no DB here, so we set it by hand.
        ReflectionTestUtils.setField(link, "createdAt", Instant.parse("2026-11-13T15:00:00Z"));
        return link;
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(service.create(URL, "bf26")).thenReturn(link("bf26", URL));

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "alias": "bf26"}""".formatted(URL)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/links/bf26")))
                .andExpect(jsonPath("$.code").value("bf26"))
                .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/bf26"))
                .andExpect(jsonPath("$.targetUrl").value(URL))
                .andExpect(jsonPath("$.createdAt").value("2026-11-13T15:00:00Z"))
                .andExpect(jsonPath("$.id").doesNotExist()); // the entity's id never leaks
    }

    @Test
    void createWithoutAliasPassesNull() throws Exception {
        when(service.create(URL, null)).thenReturn(link("x7k2p9", URL));

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s"}""".formatted(URL)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("x7k2p9"));
    }

    @Test
    void invalidInputReturns400ProblemDetail() throws Exception {
        when(service.create(any(), any()))
                .thenThrow(new InvalidUrlException("URL must start with http:// or https://"));

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "javascript:alert(1)"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("URL must start with http:// or https://"));
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void takenAliasReturns409() throws Exception {
        when(service.create(any(), any())).thenThrow(new AliasTakenException("bf26"));

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s", "alias": "bf26"}""".formatted(URL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Alias 'bf26' is already taken"));
    }

    @Test
    void codeGenerationFailureReturns500WithGenericMessage() throws Exception {
        when(service.create(any(), any())).thenThrow(new CodeGenerationException("No free code after 5 attempts"));

        mvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "%s"}""".formatted(URL)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Could not create a short link right now, please try again"));
    }

    @Test
    void getReturns200() throws Exception {
        when(service.getByCode("bf26")).thenReturn(link("bf26", URL));

        mvc.perform(get("/api/links/bf26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("bf26"));
    }

    @Test
    void getUnknownReturns404() throws Exception {
        when(service.getByCode("nope42")).thenThrow(new LinkNotFoundException("nope42"));

        mvc.perform(get("/api/links/nope42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void listUsesDefaultsAndReturnsPage() throws Exception {
        var page = new PageImpl<>(List.of(link("bf26", URL)), PageRequest.of(0, 20), 1);
        when(service.findPage(0, 20)).thenReturn(page);

        mvc.perform(get("/api/links"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].code").value("bf26"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listPassesPageAndSizeToService() throws Exception {
        when(service.findPage(2, 50)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 50), 0));

        mvc.perform(get("/api/links").param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        verify(service).findPage(2, 50);
    }

    @Test
    void updateReturns200WithNewUrl() throws Exception {
        when(service.updateTargetUrl("bf26", "https://example.com/new"))
                .thenReturn(link("bf26", "https://example.com/new"));

        mvc.perform(patch("/api/links/bf26")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"url": "https://example.com/new"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUrl").value("https://example.com/new"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/links/bf26"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete("bf26");
    }

    @Test
    void deleteUnknownReturns404() throws Exception {
        doThrow(new LinkNotFoundException("nope42")).when(service).delete("nope42");

        mvc.perform(delete("/api/links/nope42"))
                .andExpect(status().isNotFound());
    }
}
