package io.github.sebastianbadillo.linkly.controller;

import io.github.sebastianbadillo.linkly.clicks.ClickCounter;
import io.github.sebastianbadillo.linkly.service.LinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The hot path: GET /{code} -> 302 + Location. This is the endpoint the cache (M2) and k6 (M3) are about.
 * 302, not 301: browsers don't cache it, so every click reaches us (we can count it and change the target).
 */
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final LinkService service;
    private final ClickCounter clicks;

    // "/{code}" matches ONE path segment, so /api/links/... and /actuator/health never land here.
    @GetMapping("/{code}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        URI target = URI.create(service.resolveTargetUrl(code)); // unknown code: throws, nothing counted
        clicks.record(code); // only touches Valkey, never Postgres; never throws
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
