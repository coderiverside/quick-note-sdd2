package dev.quicknote.shared.pagination;

import java.util.ArrayList;
import java.util.List;

import dev.quicknote.shared.problem.DomainException;
import dev.quicknote.shared.problem.FieldError;

/**
 * Pagination bounds. Every listing is paginated — unbounded list responses are prohibited — and a
 * request above the maximum is rejected rather than silently clamped, so a client never believes it
 * received more than it did.
 */
public record PageParams(int page, int size) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public static PageParams of(Integer page, Integer size) {
        int resolvedPage = page == null ? 0 : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        List<FieldError> errors = new ArrayList<>();
        if (resolvedPage < 0) {
            errors.add(new FieldError("page", FieldError.OUT_OF_RANGE, "must be 0 or greater"));
        }
        if (resolvedSize < 1) {
            errors.add(new FieldError("size", FieldError.OUT_OF_RANGE, "must be at least 1"));
        } else if (resolvedSize > MAX_SIZE) {
            errors.add(new FieldError("size", FieldError.OUT_OF_RANGE, "must be at most " + MAX_SIZE));
        }
        if (!errors.isEmpty()) {
            throw new DomainException.ValidationFailed(errors);
        }
        return new PageParams(resolvedPage, resolvedSize);
    }

    public int offset() {
        return page * size;
    }
}
