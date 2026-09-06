package dev.quicknote.notes.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Size;

/**
 * Matches the {@code CreateNoteRequest} schema. At least one of title or body must be present.
 *
 * <p>Title and body length is deliberately <em>not</em> checked with {@code @Size} here. Bean
 * Validation counts UTF-16 units, while the domain and PostgreSQL both count code points, so a
 * {@code @Size} bound would reject notes in scripts outside the basic plane that the rest of the
 * stack accepts. The domain owns that rule and reports it with the same field-naming error shape.
 */
public record CreateNoteRequest(
        String title,
        String body,
        String color,
        Boolean pinned,

        @Size(max = 20, message = "a note carries at most 20 labels")
        List<UUID> labelIds) {}
