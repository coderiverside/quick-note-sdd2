package dev.quicknote.notes.application;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.logging.Logger;

import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteQuery;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.Page;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.notes.domain.port.NoteRepository;
import dev.quicknote.shared.correlation.CorrelationContext;
import dev.quicknote.shared.problem.DomainException;

/**
 * Use cases for notes: the transaction boundary, and where ownership is enforced.
 *
 * <p>Authorization lives here rather than in the route, so it cannot be bypassed by adding an
 * endpoint. Every operation resolves the note through {@link #requireOwned} first; a note belonging
 * to someone else is reported as absent, never as forbidden, so existence is not disclosed (FR-008).
 */
@ApplicationScoped
public class NoteService {

    private static final Logger LOG = Logger.getLogger(NoteService.class);

    private final NoteRepository notes;
    private final LabelService labels;

    @Inject
    public NoteService(NoteRepository notes, LabelService labels) {
        this.notes = notes;
        this.labels = labels;
    }

    @Transactional
    public Note create(
            UserId owner, String title, String body, String colorWire, boolean pinned, Set<LabelId> labelIds) {
        NoteColor color = resolveColor(colorWire);
        Note note = Note.create(owner, title, body, color, pinned);
        if (labelIds != null && !labelIds.isEmpty()) {
            labels.requireAllOwned(owner, labelIds);
            note.replaceLabels(labelIds);
        }
        Note saved = notes.save(note);
        // Identifiers and correlation only — never title, body, label names, or token material.
        LOG.infof(
                "note created [noteId=%s owner=%s correlationId=%s]",
                saved.id(), owner.value(), CorrelationContext.current());
        return saved;
    }

    public Note get(UserId owner, NoteId id) {
        return requireOwned(owner, id);
    }

    public Page<Note> list(NoteQuery query) {
        return notes.list(query);
    }

    @Transactional
    public Note updateContent(UserId owner, NoteId id, String title, String body, String colorWire) {
        Note note = requireOwned(owner, id);
        if (title != null || body != null) {
            note.updateContent(title, body);
        }
        if (colorWire != null) {
            note.recolor(resolveColor(colorWire));
        }
        Note saved = notes.save(note);
        LOG.infof(
                "note updated [noteId=%s owner=%s correlationId=%s]",
                saved.id(), owner.value(), CorrelationContext.current());
        return saved;
    }

    @Transactional
    public Note applyState(UserId owner, NoteId id, Boolean pinned, Boolean archived, Boolean trashed) {
        Note note = requireOwned(owner, id);

        // Restoring is the one change a trashed note accepts, so it is applied before the rest.
        if (Boolean.FALSE.equals(trashed)) {
            note.restore();
        }
        if (archived != null) {
            if (archived) {
                note.archive();
            } else {
                note.unarchive();
            }
        }
        if (pinned != null) {
            if (pinned) {
                note.pin();
            } else {
                note.unpin();
            }
        }
        if (Boolean.TRUE.equals(trashed)) {
            note.trash();
        }
        return notes.save(note);
    }

    @Transactional
    public void trash(UserId owner, NoteId id) {
        Note note = requireOwned(owner, id);
        note.trash();
        notes.save(note);
        LOG.infof(
                "note trashed [noteId=%s owner=%s correlationId=%s]",
                note.id(), owner.value(), CorrelationContext.current());
    }

    @Transactional
    public Note attachLabel(UserId owner, NoteId noteId, LabelId labelId) {
        Note note = requireOwned(owner, noteId);
        labels.requireAllOwned(owner, Set.of(labelId));
        note.attachLabel(labelId);
        return notes.save(note);
    }

    @Transactional
    public Note detachLabel(UserId owner, NoteId noteId, LabelId labelId) {
        Note note = requireOwned(owner, noteId);
        note.detachLabel(labelId);
        return notes.save(note);
    }

    @Transactional
    public Note replaceLabels(UserId owner, NoteId noteId, Set<LabelId> labelIds) {
        Note note = requireOwned(owner, noteId);
        labels.requireAllOwned(owner, labelIds);
        note.replaceLabels(labelIds);
        return notes.save(note);
    }

    /**
     * The single gate every operation passes through. Absence and another user's note are
     * indistinguishable from outside, by design.
     */
    private Note requireOwned(UserId owner, NoteId id) {
        return notes.findOwned(id, owner).orElseThrow(DomainException.NoteNotFound::new);
    }

    private static NoteColor resolveColor(String wire) {
        if (wire == null) {
            return NoteColor.DEFAULT;
        }
        return NoteColor.fromWire(wire)
                .orElseThrow(() -> new DomainException.NoteColorInvalid(NoteColor.permittedValues()));
    }

    public static NoteState stateFromFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return NoteState.ACTIVE;
        }
        return switch (filter.toLowerCase(java.util.Locale.ROOT)) {
            case "active" -> NoteState.ACTIVE;
            case "archived" -> NoteState.ARCHIVED;
            case "trashed" -> NoteState.TRASHED;
            default ->
                throw new DomainException.ValidationFailed(
                        java.util.List.of(new dev.quicknote.shared.problem.FieldError(
                                "state",
                                dev.quicknote.shared.problem.FieldError.INVALID_FORMAT,
                                "must be one of: active, archived, trashed")));
        };
    }
}
