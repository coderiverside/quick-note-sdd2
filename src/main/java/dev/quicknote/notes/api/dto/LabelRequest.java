package dev.quicknote.notes.api.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Matches the {@code LabelRequest} schema. The name is trimmed before storage.
 *
 * <p>Length is enforced in the domain rather than with {@code @Size}, for the same reason as note
 * content: Bean Validation counts UTF-16 units and the rest of the stack counts code points.
 */
public record LabelRequest(@NotNull(message = "is required") String name) {}
