package dev.quicknote.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.shared.problem.DomainException;

/** Trash transitions, exercised without a database. */
class NoteTrashTest {

    private static Note note() {
        return Note.create(UserId.of("owner-1"), "t", "b", NoteColor.DEFAULT, false);
    }

    @Test
    @DisplayName("restoring an active note returns it to active")
    void restoreToActive() {
        Note note = note();
        note.trash();
        note.restore();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
        assertThat(note.trashedAt()).isNull();
    }

    @Test
    @DisplayName("restoring an archived note returns it to archived, not to the default view")
    void restoreToArchived() {
        Note note = note();
        note.archive();
        note.trash();
        note.restore();
        assertThat(note.state()).isEqualTo(NoteState.ARCHIVED);
        assertThat(note.previousState()).isNull();
    }

    @Test
    @DisplayName("restoring a note that is not trashed does nothing")
    void restoreOfLiveNoteIsInert() {
        Note note = note();
        Instant before = note.updatedAt();
        note.restore();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
        assertThat(note.updatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("trashing twice leaves the original timestamp alone")
    void repeatedTrashKeepsTimestamp() {
        Note note = note();
        note.trash();
        Instant first = note.trashedAt();
        note.trash();
        assertThat(note.trashedAt()).isEqualTo(first);
        assertThat(note.state()).isEqualTo(NoteState.TRASHED);
    }

    @Test
    @DisplayName("a trashed note refuses every change except restoring")
    void trashedNoteRefusesEverythingElse() {
        Note note = note();
        note.trash();

        assertThatThrownBy(() -> note.updateContent("x", null)).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(() -> note.recolor(NoteColor.RED)).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(note::pin).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(note::archive).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(() -> note.attachLabel(LabelId.newId())).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(() -> note.replaceLabels(Set.of())).isInstanceOf(DomainException.NoteTrashed.class);

        note.restore();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
        note.updateContent("now allowed", null);
        assertThat(note.title()).isEqualTo("now allowed");
    }

    @Test
    @DisplayName("content, colour, and labels survive a trash-and-restore cycle")
    void contentSurvivesTheCycle() {
        Note note = Note.create(UserId.of("owner-1"), "Keep me", "with body", NoteColor.TEAL, false);
        LabelId label = LabelId.newId();
        note.attachLabel(label);

        note.trash();
        note.restore();

        assertThat(note.title()).isEqualTo("Keep me");
        assertThat(note.body()).isEqualTo("with body");
        assertThat(note.color()).isEqualTo(NoteColor.TEAL);
        assertThat(note.labels()).containsExactly(label);
    }

    @Test
    @DisplayName("trashing a pinned note clears the pin, and restoring does not bring it back")
    void pinIsNotRestored() {
        Note note = Note.create(UserId.of("owner-1"), "t", "b", NoteColor.DEFAULT, true);
        note.trash();
        note.restore();
        assertThat(note.pinned()).isFalse();
    }
}
