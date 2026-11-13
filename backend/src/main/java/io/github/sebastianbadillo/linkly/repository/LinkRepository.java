package io.github.sebastianbadillo.linkly.repository;

import io.github.sebastianbadillo.linkly.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    // Spring Data builds the query from the method name: SELECT ... FROM links WHERE code = ?
    Optional<Link> findByCode(String code);
}
