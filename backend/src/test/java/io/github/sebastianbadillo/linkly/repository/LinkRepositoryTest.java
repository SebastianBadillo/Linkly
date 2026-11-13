package io.github.sebastianbadillo.linkly.repository;

import io.github.sebastianbadillo.linkly.TestcontainersConfiguration;
import io.github.sebastianbadillo.linkly.entity.Link;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real PostgreSQL (Testcontainers) with the real Flyway migrations,
 * so the constraints tested here are the ones production uses.
 * Each test runs in a transaction that is rolled back at the end.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // don't swap in an embedded DB
@Import(TestcontainersConfiguration.class)
class LinkRepositoryTest {

    @Autowired
    private LinkRepository repository;

    @Test
    void savesAndFindsByCode() {
        repository.saveAndFlush(new Link("bf26", "https://example.com/black-friday"));

        assertThat(repository.findByCode("bf26"))
                .hasValueSatisfying(link -> {
                    assertThat(link.getId()).isNotNull();
                    assertThat(link.getTargetUrl()).isEqualTo("https://example.com/black-friday");
                    assertThat(link.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void findByCodeReturnsEmptyWhenMissing() {
        assertThat(repository.findByCode("nope42")).isEmpty();
    }

    @Test
    void rejectsDuplicateCode() {
        repository.saveAndFlush(new Link("bf26", "https://example.com/a"));

        // saveAndFlush forces the INSERT now, so the DB unique constraint fires inside the test.
        assertThatThrownBy(() -> repository.saveAndFlush(new Link("bf26", "https://example.com/b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUppercaseCode() {
        assertThatThrownBy(() -> repository.saveAndFlush(new Link("BF26", "https://example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
