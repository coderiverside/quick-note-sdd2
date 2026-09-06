package dev.quicknote.notes.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a note, wrapped so it cannot be confused with any other identifier. */
public record NoteId(UUID value) {

    public NoteId {
        Objects.requireNonNull(value, "NoteId value must not be null");
    }

    public static NoteId of(UUID value) {
        return new NoteId(value);
    }

    public static NoteId newId() {
        return new NoteId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
