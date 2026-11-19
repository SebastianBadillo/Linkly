package io.github.sebastianbadillo.linkly.apikey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Admin only: issues API keys. Protected by X-Admin-Token, not by X-API-Key
 * (ApiKeyFilter skips this endpoint, otherwise you would need a key to get your first key).
 */
@RestController
@RequestMapping("/api/keys")
public class ApiKeyController {

    private final ApiKeyService service;
    private final String adminToken;

    public ApiKeyController(ApiKeyService service, @Value("${linkly.admin-token:}") String adminToken) {
        this.service = service;
        this.adminToken = adminToken;
    }

    public record CreateKeyRequest(String name) {
    }

    @PostMapping
    public ResponseEntity<ApiKeyService.CreatedKey> create(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestBody(required = false) CreateKeyRequest request) {
        if (!isAdmin(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid admin token");
        }
        String name = (request == null || request.name() == null || request.name().isBlank())
                ? "unnamed" : request.name().strip();
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(name));
    }

    private boolean isAdmin(String token) {
        // No ADMIN_TOKEN configured = nobody is admin (never "everyone is admin").
        if (adminToken.isBlank() || token == null) {
            return false;
        }
        // Constant-time compare: equals() stops at the first wrong char, and that timing leaks information.
        return MessageDigest.isEqual(adminToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
