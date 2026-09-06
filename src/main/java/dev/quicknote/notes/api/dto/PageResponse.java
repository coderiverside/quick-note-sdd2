package dev.quicknote.notes.api.dto;

import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.quicknote.notes.domain.Page;

/** The paginated envelope every listing returns. Unbounded list responses are prohibited. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages, String nextPage) {

    public static <D, T> PageResponse<T> from(Page<D> page, Function<D, T> mapper, String basePath) {
        String next = page.hasNext()
                ? basePath + (basePath.contains("?") ? "&" : "?") + "page=" + (page.page() + 1) + "&size=" + page.size()
                : null;
        return new PageResponse<>(
                page.items().stream().map(mapper).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                next);
    }
}
