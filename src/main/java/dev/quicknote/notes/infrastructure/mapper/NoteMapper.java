package dev.quicknote.notes.infrastructure.mapper;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.stream.Collectors;

import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.infrastructure.entity.NoteEntity;

/**
 * The entity/domain boundary.
 *
 * <p>This mapping is the cost of keeping the ORM out of the domain, and it is a cost worth paying:
 * it is what lets every invariant in {@link Note} be tested without a database.
 */
public final class NoteMapper {

    private NoteMapper() {}

    public static Note toDomain(NoteEntity entity) {
        return Note.rehydrate(
                NoteId.of(entity.id),
                UserId.of(entity.ownerId),
                entity.title,
                entity.body,
                NoteColor.fromWire(entity.color).orElse(NoteColor.DEFAULT),
                entity.pinned,
                NoteState.valueOf(entity.state),
                entity.previousState == null ? null : NoteState.valueOf(entity.previousState),
                entity.trashedAt,
                entity.createdAt,
                entity.updatedAt,
                entity.labelIds.stream().map(LabelId::of).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** Copies domain state onto a managed entity; the caller decides whether it is new or attached. */
    public static void apply(Note note, NoteEntity entity) {
        entity.id = note.id().value();
        entity.ownerId = note.ownerId().value();
        entity.title = note.title();
        entity.body = note.body();
        entity.color = note.color().wireName();
        entity.pinned = note.pinned();
        entity.state = note.state().name().toUpperCase(Locale.ROOT);
        entity.previousState =
                note.previousState() == null ? null : note.previousState().name();
        entity.trashedAt = note.trashedAt();
        entity.createdAt = note.createdAt();
        entity.updatedAt = note.updatedAt();
        entity.labelIds =
                note.labels().stream().map(LabelId::value).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static NoteEntity toEntity(Note note) {
        NoteEntity entity = new NoteEntity();
        apply(note, entity);
        return entity;
    }
}
