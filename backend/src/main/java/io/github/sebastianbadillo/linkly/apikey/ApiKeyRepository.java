package io.github.sebastianbadillo.linkly.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    // SELECT ... FROM api_keys WHERE key_hash = ?  (uses the unique index)
    Optional<ApiKey> findByKeyHash(String keyHash);
}
