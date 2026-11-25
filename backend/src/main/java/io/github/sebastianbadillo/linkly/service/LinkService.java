package io.github.sebastianbadillo.linkly.service;

import io.github.sebastianbadillo.linkly.cache.LinkCache;
import io.github.sebastianbadillo.linkly.clicks.ClickCounter;
import io.github.sebastianbadillo.linkly.entity.Link;
import io.github.sebastianbadillo.linkly.exception.AliasTakenException;
import io.github.sebastianbadillo.linkly.exception.CodeGenerationException;
import io.github.sebastianbadillo.linkly.exception.LinkNotFoundException;
import io.github.sebastianbadillo.linkly.repository.LinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor // Lombok writes the constructor for the final fields (constructor injection)
public class LinkService {

    static final int MAX_CODE_ATTEMPTS = 5;
    static final int MAX_PAGE_SIZE = 100;

    private final LinkRepository repository;
    private final ShortCodeGenerator generator;
    private final AliasValidator aliasValidator;
    private final TargetUrlValidator urlValidator;
    private final LinkCache cache;
    private final ClickCounter clicks;

    /**
     * NOT @Transactional on purpose. Each saveAndFlush runs in its own small transaction,
     * so when one attempt hits the unique constraint, only that attempt is rolled back
     * and the next attempt starts clean.
     */
    public Link create(String url, String alias) {
        String targetUrl = urlValidator.validate(url);

        if (alias != null) {
            String code = aliasValidator.normalize(alias);
            Link saved;
            try {
                saved = repository.saveAndFlush(new Link(code, targetUrl));
            } catch (DataIntegrityViolationException e) {
                // The client chose this exact alias, so retrying with another one makes no sense: 409.
                throw new AliasTakenException(code);
            }
            cache.evict(code); // removes a "not found" marker someone may have cached for this alias
            return saved;
        }

        for (int attempt = 1; attempt <= MAX_CODE_ATTEMPTS; attempt++) {
            String code = generator.generate();
            try {
                Link saved = repository.saveAndFlush(new Link(code, targetUrl));
                cache.evict(code);
                return saved;
            } catch (DataIntegrityViolationException e) {
                log.warn("Generated code '{}' collided (attempt {}/{})", code, attempt, MAX_CODE_ATTEMPTS);
            }
        }
        log.error("Could not generate a free short code after {} attempts", MAX_CODE_ATTEMPTS);
        throw new CodeGenerationException("No free code after %d attempts".formatted(MAX_CODE_ATTEMPTS));
    }

    /**
     * The redirect hot path, cache-aside:
     * 1. ask Valkey; a HIT answers without touching Postgres (URL -> redirect, NOT_FOUND marker -> 404)
     * 2. MISS -> ask Postgres, then store the answer in Valkey for the next request
     */
    public String resolveTargetUrl(String code) {
        String normalized = code.toLowerCase(Locale.ROOT);

        Optional<String> cached = cache.get(normalized);
        if (cached.isPresent()) {
            if (LinkCache.NOT_FOUND.equals(cached.get())) {
                throw new LinkNotFoundException(normalized);
            }
            return cached.get();
        }

        Optional<Link> link = repository.findByCode(normalized);
        if (link.isEmpty()) {
            cache.putNotFound(normalized);
            throw new LinkNotFoundException(normalized);
        }
        String targetUrl = link.get().getTargetUrl();
        cache.putUrl(normalized, targetUrl);
        return targetUrl;
    }

    public Link getByCode(String code) {
        // Every code in the DB is lowercase, so BF26 and bf26 find the same link.
        String normalized = code.toLowerCase(Locale.ROOT);
        return repository.findByCode(normalized)
                .orElseThrow(() -> new LinkNotFoundException(normalized));
    }

    /** Newest first. Bad page/size values are clamped instead of rejected. */
    public Page<Link> findPage(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE); // size=500 -> 100, size=0 -> 1
        // id as tie-breaker: two links with the same created_at keep a fixed order between pages.
        Sort newestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
        return repository.findAll(PageRequest.of(safePage, safeSize, newestFirst));
    }

    /**
     * NOT @Transactional on purpose: the evict must run AFTER the commit. Inside a transaction,
     * a redirect could re-cache the OLD url between our evict and the commit.
     * saveAndFlush runs (and commits) its own transaction, THEN we evict.
     */
    public Link updateTargetUrl(String code, String url) {
        String targetUrl = urlValidator.validate(url);
        Link link = getByCode(code);
        link.setTargetUrl(targetUrl);
        Link saved = repository.saveAndFlush(link);
        cache.evict(link.getCode());
        return saved;
    }

    /** Same rule: Postgres first (delete commits in its own transaction), THEN evict. */
    public void delete(String code) {
        Link link = getByCode(code);
        repository.delete(link);
        cache.evict(link.getCode());
        clicks.discard(link.getCode()); // after the evict: redirects now get 404 and add no new clicks
    }
}
