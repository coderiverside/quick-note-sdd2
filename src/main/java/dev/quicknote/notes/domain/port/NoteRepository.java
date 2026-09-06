package dev.quicknote.notes.domain.port;

import java.time.Instant;
import java.util.Optional;

import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteQuery;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;

/**
 * The persistence contract, owned by the domain and implemented in infrastructure.
 *
 * <p>Every lookup takes the owner. There is deliberately no "find by id" that ignores ownership, so
 * a caller cannot accidentally reach another user's note.
 */
public interface NoteRepository {

    Note save(Note note);

    Optional<Note> findOwned(NoteId id, UserId owner);

    Page<Note> list(NoteQuery query);

    /** Permanently removes one trashed note. Irreversible. */
    boolean purge(NoteId id, UserId owner);

    /** Permanently removes every trashed note for one owner. Returns how many were removed. */
    long purgeAllTrashed(UserId owner);

    /** The retention sweep: removes notes trashed before the cutoff, in bounded batches. */
    long purgeTrashedBefore(Instant cutoff, int batchSize);

    /** Detaches a label from every note carrying it, without deleting any note. */
    void detachLabelEverywhere(dev.quicknote.notes.domain.LabelId labelId, UserId owner);
}
