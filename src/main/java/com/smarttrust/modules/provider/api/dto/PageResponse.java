package com.smarttrust.modules.provider.api.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable JSON shape for paginated admin lists (avoids Spring's unstable PageImpl serialization). */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getTotalElements(), p.getTotalPages(),
                p.getNumber(), p.getSize());
    }
}
