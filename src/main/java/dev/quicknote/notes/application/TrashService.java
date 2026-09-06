package dev.quicknote.notes.application;

import java.time.Duration;
import java.time.Instant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteQuery;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.domain.port.NoteRepository;
import dev.quicknote.platform.config.QuickNoteConfig;
import dev.quicknote.shared.correlation.CorrelationContext;
import dev.quicknote.shared.problem.DomainException;

/**
 * The safety net.
 *
 * <p>Deleting a note is reversible for the retention window; purging it is not. The two are separate
 * operations on separate resources precisely so that the irreversible one cannot be reached by
 * accident.
 */
@ApplicationScoped
public class TrashService {

    private static final Logger LOG = Logger.getLogger(TrashService.class);

    private final NoteRepository notes;
    private final QuickNoteConfig config;

    @Inject
    public TrashService(NoteRepository notes, QuickNoteConfig config) {
        this.notes = notes;
        this.config = config;
    }

    public Page<Note> list(UserId owner, int page, int size) {
        return notes.list(NoteQuery.of(owner, NoteState.TRASHED, page, size));
    }

    /** Permanently removes one trashed note. A note that is not in the trash is reported as absent. */
    @Transactional
    public void purge(UserId owner, NoteId id) {
        Note note = notes.findOwned(id, owner).orElseThrow(DomainException.NoteNotFound::new);
        if (!note.state().isTrashed()) {
            // Permanent deletion is reachable only through the trash, never straight from a live note.
            throw new DomainException.NoteNotFound();
        }
        notes.purge(id, owner);
        LOG.infof("note purged [noteId=%s owner=%s correlationId=%s]", id, owner.value(), CorrelationContext.current());
    }

    /** Empties the trash. Active and archived notes are untouched, and no label is deleted. */
    @Transactional
    public long empty(UserId owner) {
        long removed = notes.purgeAllTrashed(owner);
        LOG.infof(
                "trash emptied [owner=%s removed=%d correlationId=%s]",
                owner.value(), removed, CorrelationContext.current());
        return removed;
    }

    /** The retention sweep, in bounded batches so a long lock never forms. */
    @Transactional
    public long purgeExpired(int batchSize) {
        Instant cutoff = Instant.now().minus(Duration.ofDays(config.trash().retentionDays()));
        return notes.purgeTrashedBefore(cutoff, batchSize);
    }

    public int retentionDays() {
        return config.trash().retentionDays();
    }
}
