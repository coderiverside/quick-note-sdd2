package dev.quicknote.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.shared.problem.DomainException;

/** The invariants, exercised without a container or a database — which is the point of the layering. */
class NoteTest {

    private static final UserId OWNER = UserId.of("owner-1");

    private static Note note(String title, String body) {
        return Note.create(OWNER, title, body, NoteColor.DEFAULT, false);
    }

    @Test
    @DisplayName("a note needs at least a title or a body")
    void contentRequired() {
        assertThatThrownBy(() -> note(null, null)).isInstanceOf(DomainException.NoteContentRequired.class);
        assertThat(note("Groceries", null).title()).isEqualTo("Groceries");
        assertThat(note(null, "milk").body()).isEqualTo("milk");
    }

    @Test
    @DisplayName("whitespace-only content counts as empty")
    void whitespaceIsEmpty() {
        assertThatThrownBy(() -> note("   ", "\n\t ")).isInstanceOf(DomainException.NoteContentRequired.class);
        assertThat(note("  Groceries  ", "   ").body()).isNull();
    }

    @Test
    @DisplayName("content is trimmed but otherwise preserved exactly")
    void contentPreserved() {
        Note n = note("  Title  ", "  line one\nline two — emoji 🎉 日本語  ");
        assertThat(n.title()).isEqualTo("Title");
        assertThat(n.body()).isEqualTo("line one\nline two — emoji 🎉 日本語");
    }

    @Test
    @DisplayName("length limits count characters, not bytes")
    void lengthLimitsCountCharacters() {
        String maxBody = "日".repeat(Note.MAX_BODY_LENGTH);
        assertThat(note(null, maxBody).body()).hasSize(Note.MAX_BODY_LENGTH);

        assertThatThrownBy(() -> note(null, "a".repeat(Note.MAX_BODY_LENGTH + 1)))
                .isInstanceOf(DomainException.ValidationFailed.class);
        assertThatThrownBy(() -> note("a".repeat(Note.MAX_TITLE_LENGTH + 1), null))
                .isInstanceOf(DomainException.ValidationFailed.class);
    }

    @Test
    @DisplayName("a new note is active, unpinned, untrashed, and default-coloured")
    void defaults() {
        Note n = note("t", "b");
        assertThat(n.state()).isEqualTo(NoteState.ACTIVE);
        assertThat(n.pinned()).isFalse();
        assertThat(n.color()).isEqualTo(NoteColor.DEFAULT);
        assertThat(n.trashedAt()).isNull();
        assertThat(n.id()).isNotNull();
        assertThat(n.createdAt()).isNotNull().isEqualTo(n.updatedAt());
        assertThat(n.labels()).isEmpty();
    }

    @Test
    @DisplayName("editing advances the last-updated timestamp")
    void editAdvancesTimestamp() throws Exception {
        Note n = note("t", "b");
        Instant before = n.updatedAt();
        Thread.sleep(2);
        n.updateContent("new title", "new body");
        assertThat(n.updatedAt()).isAfter(before);
        assertThat(n.createdAt()).isBefore(n.updatedAt());
    }

    @Test
    @DisplayName("an update that would empty both title and body is refused")
    void updateCannotEmptyBoth() {
        Note n = note("t", "b");
        assertThatThrownBy(() -> n.updateContent(Note.CLEAR, Note.CLEAR))
                .isInstanceOf(DomainException.NoteContentRequired.class);
        assertThat(n.title()).as("the note is unchanged after a refused update").isEqualTo("t");
    }

    @Test
    @DisplayName("an absent value leaves a field untouched; an explicit clear empties it")
    void partialUpdateSemantics() {
        Note n = note("t", "b");
        n.updateContent(null, "only body changed");
        assertThat(n.title()).isEqualTo("t");
        assertThat(n.body()).isEqualTo("only body changed");

        n.updateContent(Note.CLEAR, null);
        assertThat(n.title()).isNull();
        assertThat(n.body()).isEqualTo("only body changed");
    }

    @Test
    @DisplayName("ownership is fixed at creation")
    void ownershipIsImmutable() {
        Note n = note("t", "b");
        assertThat(n.ownerId()).isEqualTo(OWNER);
        assertThat(n.isOwnedBy(OWNER)).isTrue();
        assertThat(n.isOwnedBy(UserId.of("someone-else"))).isFalse();
    }

    @Test
    @DisplayName("trashing clears pinned and records when it happened")
    void trashingClearsPinned() {
        Note n = Note.create(OWNER, "t", "b", NoteColor.DEFAULT, true);
        assertThat(n.pinned()).isTrue();
        n.trash();
        assertThat(n.state()).isEqualTo(NoteState.TRASHED);
        assertThat(n.pinned()).isFalse();
        assertThat(n.trashedAt()).isNotNull();
    }

    @Test
    @DisplayName("trashing an already-trashed note leaves the original timestamp alone")
    void repeatedTrashIsInert() {
        Note n = note("t", "b");
        n.trash();
        Instant first = n.trashedAt();
        n.trash();
        assertThat(n.trashedAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("a trashed note refuses content edits until it is restored")
    void trashedNoteRefusesEdits() {
        Note n = note("t", "b");
        n.trash();
        assertThatThrownBy(() -> n.updateContent("new", null)).isInstanceOf(DomainException.NoteTrashed.class);
    }

    @Test
    @DisplayName("a colour outside the palette cannot be represented at all")
    void colourIsAClosedSet() {
        Note n = note("t", "b");
        n.recolor(NoteColor.TEAL);
        assertThat(n.color()).isEqualTo(NoteColor.TEAL);
    }
}
