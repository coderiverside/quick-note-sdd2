package dev.quicknote.notes.domain;

import java.util.List;

/**
 * A page of results, expressed without any transport type so the application layer can return it
 * directly.
 */
public record Page<T>(List<T> items, int page, int size, long totalElements) {

    public Page {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < totalElements;
    }

    public <R> Page<R> map(java.util.function.Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), page, size, totalElements);
    }
}
