package io.github.sebastianbadillo.linkly.controller;

import io.github.sebastianbadillo.linkly.dto.CreateLinkRequest;
import io.github.sebastianbadillo.linkly.dto.LinkResponse;
import io.github.sebastianbadillo.linkly.dto.PageResponse;
import io.github.sebastianbadillo.linkly.dto.UpdateLinkRequest;
import io.github.sebastianbadillo.linkly.entity.Link;
import io.github.sebastianbadillo.linkly.service.LinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** The management API. Thin on purpose: HTTP in and out here, all rules live in LinkService. */
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private final LinkService service;
    private final String baseUrl;

    public LinkController(LinkService service, @Value("${linkly.base-url}") String baseUrl) {
        this.service = service;
        // "https://linkly.app/" and "https://linkly.app" both work: drop the trailing slash.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@RequestBody CreateLinkRequest request) {
        Link link = service.create(request.url(), request.alias());
        // Location: where the new resource lives, e.g. http://localhost:8080/api/links/bf26
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{code}")
                .buildAndExpand(link.getCode())
                .toUri();
        return ResponseEntity.created(location).body(toResponse(link)); // 201
    }

    @GetMapping("/{code}")
    public LinkResponse get(@PathVariable String code) {
        return toResponse(service.getByCode(code));
    }

    @GetMapping
    public PageResponse<LinkResponse> list(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.findPage(page, size).map(this::toResponse));
    }

    @PatchMapping("/{code}")
    public LinkResponse update(@PathVariable String code, @RequestBody UpdateLinkRequest request) {
        return toResponse(service.updateTargetUrl(code, request.url()));
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT) // 204: it worked, nothing to send back
    public void delete(@PathVariable String code) {
        service.delete(code);
    }

    private LinkResponse toResponse(Link link) {
        return LinkResponse.from(link, baseUrl);
    }
}
