package io.github.sebastianbadillo.linkly.apikey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Creates keys and checks them. The raw key ("lk_...") leaves this class only once, in {@link #create};
 * after that we can only compare hashes.
 */
@Service
public class ApiKeyService {

    static final String PREFIX = "lk_";

    // SecureRandom, not Random: keys are secrets, so they must be unpredictable.
    private final SecureRandom random = new SecureRandom();
    private final ApiKeyRepository repository;

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /** The response of POST /api/keys. The only moment the raw key is visible. */
    public record CreatedKey(Long id, String key) {
    }

    @Transactional
    public CreatedKey create(String name) {
        byte[] bytes = new byte[32]; // 256 random bits: impossible to guess
        random.nextBytes(bytes);
        String rawKey = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ApiKey saved = repository.save(new ApiKey(sha256(rawKey), name));
        return new CreatedKey(saved.getId(), rawKey);
    }

    /** The active key matching this raw value; empty if it is missing, unknown or revoked. */
    @Transactional(readOnly = true)
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByKeyHash(sha256(rawKey)).filter(key -> !key.isRevoked());
    }

    /**
     * A fast hash is fine here, unlike for passwords: the key is 256 random bits, so there is
     * nothing to brute-force. Passwords are short and guessable, so they need a slow hash (bcrypt/argon2).
     */
    static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }
}
