package dev.quicknote.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.shared.problem.DomainException;

/** The state machine: pinned never coexists with archived or trashed, in any order of operations. */
class NoteStateTransitionTest {

    private static final UserId OWNER = UserId.of("owner-1");

    private static Note pinnedNote() {
        return Note.create(OWNER, "t", "b", NoteColor.DEFAULT, true);
    }

    @Test
    @DisplayName("archiving clears pinned")
    void archivingClearsPinned() {
        Note note = pinnedNote();
        note.archive();
        assertThat(note.state()).isEqualTo(NoteState.ARCHIVED);
        assertThat(note.pinned()).isFalse();
    }

    @Test
    @DisplayName("pinning an archived note brings it back to active rather than creating an illegal pair")
    void pinningAnArchivedNoteUnarchivesIt() {
        Note note = Note.create(OWNER, "t", "b", NoteColor.DEFAULT, false);
        note.archive();
        note.pin();
        assertThat(note.pinned()).isTrue();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
    }

    @Test
    @DisplayName("unarchiving returns the note to active without re-pinning it")
    void unarchivingDoesNotRestorePin() {
        Note note = pinnedNote();
        note.archive();
        note.unarchive();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
        assertThat(note.pinned())
                .as("the pin was cleared by archiving and is not silently restored")
                .isFalse();
    }

    @Test
    @DisplayName("archiving twice is a no-op")
    void archivingIsIdempotent() {
        Note note = Note.create(OWNER, "t", "b", NoteColor.DEFAULT, false);
        note.archive();
        note.archive();
        assertThat(note.state()).isEqualTo(NoteState.ARCHIVED);
    }

    @Test
    @DisplayName("trashing an archived note remembers where to put it back")
    void trashRemembersPreviousState() {
        Note note = Note.create(OWNER, "t", "b", NoteColor.DEFAULT, false);
        note.archive();
        note.trash();
        assertThat(note.previousState()).isEqualTo(NoteState.ARCHIVED);
        note.restore();
        assertThat(note.state())
                .as("an archived note must not resurface in the default view")
                .isEqualTo(NoteState.ARCHIVED);
    }

    @Test
    @DisplayName("a trashed note refuses pin, archive, and colour changes")
    void trashedNoteRefusesStateChanges() {
        Note note = Note.create(OWNER, "t", "b", NoteColor.DEFAULT, false);
        note.trash();
        assertThatThrownBy(note::pin).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(note::archive).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(() -> note.recolor(NoteColor.RED)).isInstanceOf(DomainException.NoteTrashed.class);
    }

    @Test
    @DisplayName("pinned is never true outside the active state, whatever the path taken")
    void pinnedOnlyEverCoexistsWithActive() {
        for (Runnable path : new Runnable[] {
            () -> {
                Note n = pinnedNote();
                n.archive();
                assertThat(n.pinned() && n.state() != NoteState.ACTIVE).isFalse();
            },
            () -> {
                Note n = pinnedNote();
                n.trash();
                assertThat(n.pinned() && n.state() != NoteState.ACTIVE).isFalse();
            },
            () -> {
                Note n = pinnedNote();
                n.archive();
                n.trash();
                n.restore();
                assertThat(n.pinned() && n.state() != NoteState.ACTIVE).isFalse();
            }
        }) {
            path.run();
        }
    }

    @Test
    @DisplayName("colour changes independently of state")
    void colourIsIndependentOfState() {
        Note note = Note.create(OWNER, "t", "b", NoteColor.DEFAULT, false);
        note.recolor(NoteColor.TEAL);
        note.archive();
        assertThat(note.color()).isEqualTo(NoteColor.TEAL);
    }
}
