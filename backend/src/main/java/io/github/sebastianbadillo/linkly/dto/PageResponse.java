package io.github.sebastianbadillo.linkly.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Our own page format. Returning Spring's Page directly would leak its internal JSON shape,
 * which can change between Spring versions.
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
