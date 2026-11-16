package io.github.sebastianbadillo.linkly.controller;

import io.github.sebastianbadillo.linkly.exception.LinkNotFoundException;
import io.github.sebastianbadillo.linkly.service.LinkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RedirectController.class)
class RedirectControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LinkService service;

    @Test
    void redirectsWith302AndLocation() throws Exception {
        when(service.resolveTargetUrl("bf26")).thenReturn("https://example.com/black-friday");

        mvc.perform(get("/bf26"))
                .andExpect(status().isFound()) // 302
                .andExpect(header().string("Location", "https://example.com/black-friday"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        when(service.resolveTargetUrl("nope42")).thenThrow(new LinkNotFoundException("nope42"));

        mvc.perform(get("/nope42"))
                .andExpect(status().isNotFound());
    }
}
