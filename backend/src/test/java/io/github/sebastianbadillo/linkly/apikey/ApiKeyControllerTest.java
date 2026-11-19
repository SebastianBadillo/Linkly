package io.github.sebastianbadillo.linkly.apikey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ApiKeyController.class, properties = "linkly.admin-token=s3cret")
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ApiKeyService service;

    @Test
    void correctAdminTokenCreatesAKey() throws Exception {
        when(service.create("k6")).thenReturn(new ApiKeyService.CreatedKey(1L, "lk_abc"));

        mvc.perform(post("/api/keys")
                        .header("X-Admin-Token", "s3cret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "k6"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.key").value("lk_abc"));
    }

    @Test
    void wrongAdminTokenIs403() throws Exception {
        mvc.perform(post("/api/keys").header("X-Admin-Token", "guess"))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any());
    }

    @Test
    void missingAdminTokenIs403() throws Exception {
        mvc.perform(post("/api/keys"))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any());
    }
}
