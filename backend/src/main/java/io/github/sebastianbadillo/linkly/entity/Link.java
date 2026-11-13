package io.github.sebastianbadillo.linkly.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A short link. The schema is owned by Flyway (V1__create_links.sql);
 * Hibernate only validates that this class matches it.
 */
@Entity
@Table(name = "links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs it; the rest of the code uses the constructor below
public class Link {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // the DB generates the id
    private Long id;

    @Setter
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Setter
    @Column(name = "target_url", nullable = false, columnDefinition = "TEXT")
    private String targetUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Link(String code, String targetUrl) {
        this.code = code;
        this.targetUrl = targetUrl;
    }
}
