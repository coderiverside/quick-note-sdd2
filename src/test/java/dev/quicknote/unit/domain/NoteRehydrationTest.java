package dev.quicknote.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.notes.domain.LabelId;
import dev.quicknote.notes.domain.Note;
import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteId;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.shared.problem.DomainException;

/**
 * Rehydration re-checks the invariants, because storage can be wrong too.
 *
 * <p>A row can be written by a migration, by a future code path, or by someone at a psql prompt. The
 * aggregate refuses to represent an illegal note whatever the source, and these are the guards that
 * make that true rather than merely intended.
 */
class NoteRehydrationTest {

    private static final UserId OWNER = UserId.of("owner-1");
    private static final Instant NOW = Instant.parse("2026-08-26T10:00:00Z");

    private static Note rehydrate(
            String title,
            String body,
            NoteColor color,
            boolean pinned,
            NoteState state,
            NoteState previousState,
            Instant trashedAt,
            Set<LabelId> labels) {
        return Note.rehydrate(
                NoteId.newId(), OWNER, title, body, color, pinned, state, previousState, trashedAt, NOW, NOW, labels);
    }

    @Test
    @DisplayName("a null colour or state rehydrates to the documented default")
    void nullsBecomeDefaults() {
        Note note = rehydrate("t", "b", null, false, null, null, null, Set.of());
        assertThat(note.color()).isEqualTo(NoteColor.DEFAULT);
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
    }

    @Test
    @DisplayName("a stored row with neither title nor body is refused")
    void contentInvariantHoldsOnRehydration() {
        assertThatThrownBy(
                        () -> rehydrate(null, null, NoteColor.DEFAULT, false, NoteState.ACTIVE, null, null, Set.of()))
                .isInstanceOf(DomainException.NoteContentRequired.class);
    }

    @Test
    @DisplayName("a stored row that is pinned and archived is refused")
    void pinnedArchivedIsRefused() {
        assertThatThrownBy(() -> rehydrate("t", "b", NoteColor.DEFAULT, true, NoteState.ARCHIVED, null, null, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned must not coexist with ARCHIVED");
    }

    @Test
    @DisplayName("a stored row that is pinned and trashed is refused")
    void pinnedTrashedIsRefused() {
        assertThatThrownBy(() -> rehydrate("t", "b", NoteColor.DEFAULT, true, NoteState.TRASHED, null, NOW, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pinned must not coexist with TRASHED");
    }

    @Test
    @DisplayName("trashedAt must be set exactly when the note is trashed")
    void trashedAtMustMatchState() {
        assertThatThrownBy(() -> rehydrate("t", "b", NoteColor.DEFAULT, false, NoteState.TRASHED, null, null, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trashedAt");

        assertThatThrownBy(() -> rehydrate("t", "b", NoteColor.DEFAULT, false, NoteState.ACTIVE, null, NOW, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trashedAt");
    }

    @Test
    @DisplayName("a stored row carrying more than twenty labels is refused")
    void labelCeilingHoldsOnRehydration() {
        Set<LabelId> twentyOne = IntStream.range(0, 21)
                .mapToObj(i -> LabelId.newId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThatThrownBy(() -> rehydrate("t", "b", NoteColor.DEFAULT, false, NoteState.ACTIVE, null, null, twentyOne))
                .isInstanceOf(DomainException.NoteLabelLimitExceeded.class);
    }

    @Test
    @DisplayName("a trashed row with no recorded previous state restores to active")
    void restoreWithoutPreviousStateFallsBackToActive() {
        Note note = rehydrate("t", "b", NoteColor.DEFAULT, false, NoteState.TRASHED, null, NOW, Set.of());
        note.restore();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
    }

    @Test
    @DisplayName("unarchiving a note that is already active does nothing")
    void unarchiveOfActiveNoteIsInert() {
        Note note = rehydrate("t", "b", NoteColor.DEFAULT, false, NoteState.ACTIVE, null, null, Set.of());
        Instant before = note.updatedAt();
        note.unarchive();
        assertThat(note.state()).isEqualTo(NoteState.ACTIVE);
        assertThat(note.updatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("a rehydrated note keeps its stored timestamps")
    void timestampsArePreserved() {
        Note note = rehydrate("t", "b", NoteColor.TEAL, false, NoteState.ACTIVE, null, null, Set.of());
        assertThat(note.createdAt()).isEqualTo(NOW);
        assertThat(note.updatedAt()).isEqualTo(NOW);
        assertThat(note.color()).isEqualTo(NoteColor.TEAL);
    }
}
