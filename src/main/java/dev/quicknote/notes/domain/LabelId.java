package dev.quicknote.notes.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a label, wrapped so it cannot be confused with any other identifier. */
public record LabelId(UUID value) {

    public LabelId {
        Objects.requireNonNull(value, "LabelId value must not be null");
    }

    public static LabelId of(UUID value) {
        return new LabelId(value);
    }

    public static LabelId newId() {
        return new LabelId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
