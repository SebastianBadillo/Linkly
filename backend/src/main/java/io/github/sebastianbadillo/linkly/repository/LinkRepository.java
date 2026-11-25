package io.github.sebastianbadillo.linkly.repository;

import io.github.sebastianbadillo.linkly.entity.Link;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    // Spring Data builds the query from the method name: SELECT ... FROM links WHERE code = ?
    Optional<Link> findByCode(String code);

    /**
     * Adds a batch of clicks in ONE statement. "clicks + :amount" is computed inside Postgres,
     * so two instances adding at the same time can't overwrite each other.
     * Returns the number of rows changed: 0 means the link no longer exists.
     */
    @Modifying // this query writes, it doesn't select
    @Transactional
    @Query("UPDATE Link l SET l.clicks = l.clicks + :amount WHERE l.code = :code")
    int addClicks(@Param("code") String code, @Param("amount") long amount);
}
