package dev.quicknote.notes.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.quicknote.notes.domain.Label;

/** The wire shape of a label. The submitted casing is preserved for display. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LabelResponse(UUID id, String name, Instant createdAt) {

    public static LabelResponse from(Label label) {
        return new LabelResponse(label.id().value(), label.name(), label.createdAt());
    }
}
