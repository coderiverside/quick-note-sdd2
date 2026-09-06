package dev.quicknote.notes.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteState;

/**
 * The wire shape of a note.
 *
 * <p>{@code archived} and {@code trashed} are the wire form of one three-valued state; the mapping is
 * total and they are never both true.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record NoteResponse(
        UUID id,
        String title,
        String body,
        String color,
        boolean pinned,
        boolean archived,
        boolean trashed,
        Instant trashedAt,
        List<UUID> labelIds,
        Instant createdAt,
        Instant updatedAt) {

    public static NoteResponse from(Note note) {
        return new NoteResponse(
                note.id().value(),
                note.title(),
                note.body(),
                note.color().wireName(),
                note.pinned(),
                note.state() == NoteState.ARCHIVED,
                note.state() == NoteState.TRASHED,
                note.trashedAt(),
                note.labels().stream().map(l -> l.value()).toList(),
                note.createdAt(),
                note.updatedAt());
    }
}
