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
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.shared.problem.DomainException;

/** Attachment rules: idempotent both ways, and capped. */
class NoteLabelTest {

    private static Note note() {
        return Note.create(UserId.of("owner-1"), "t", "b", NoteColor.DEFAULT, false);
    }

    private static Set<LabelId> labels(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> LabelId.newId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Test
    @DisplayName("attaching the same label twice changes nothing")
    void duplicateAttachIsANoOp() {
        Note note = note();
        LabelId label = LabelId.newId();
        note.attachLabel(label);
        Instant afterFirst = note.updatedAt();
        note.attachLabel(label);

        assertThat(note.labels()).containsExactly(label);
        assertThat(note.updatedAt()).as("a no-op must not look like a change").isEqualTo(afterFirst);
    }

    @Test
    @DisplayName("detaching a label the note does not carry changes nothing")
    void detachingUnattachedIsANoOp() {
        Note note = note();
        Instant before = note.updatedAt();
        note.detachLabel(LabelId.newId());
        assertThat(note.labels()).isEmpty();
        assertThat(note.updatedAt()).isEqualTo(before);
    }

    @Test
    @DisplayName("detaching removes only the named label")
    void detachRemovesOne() {
        Note note = note();
        Set<LabelId> three = labels(3);
        note.replaceLabels(three);
        LabelId removed = three.iterator().next();
        note.detachLabel(removed);
        assertThat(note.labels()).hasSize(2).doesNotContain(removed);
    }

    @Test
    @DisplayName("a note carries at most twenty labels")
    void twentyIsTheCeiling() {
        Note note = note();
        note.replaceLabels(labels(20));
        assertThat(note.labels()).hasSize(20);

        assertThatThrownBy(() -> note.attachLabel(LabelId.newId()))
                .isInstanceOf(DomainException.NoteLabelLimitExceeded.class)
                .satisfies(e -> assertThat(((DomainException.NoteLabelLimitExceeded) e)
                                .error()
                                .status())
                        .isEqualTo(422));
    }

    @Test
    @DisplayName("replacing with more than twenty is refused outright")
    void replaceRespectsCeiling() {
        Note note = note();
        assertThatThrownBy(() -> note.replaceLabels(labels(21)))
                .isInstanceOf(DomainException.NoteLabelLimitExceeded.class);
        assertThat(note.labels())
                .as("a refused replace leaves the set untouched")
                .isEmpty();
    }

    @Test
    @DisplayName("replacing swaps the whole set")
    void replaceSwapsTheSet() {
        Note note = note();
        note.replaceLabels(labels(3));
        Set<LabelId> replacement = labels(2);
        note.replaceLabels(replacement);
        assertThat(note.labels()).containsExactlyInAnyOrderElementsOf(replacement);
    }

    @Test
    @DisplayName("a trashed note refuses label changes")
    void trashedNoteRefusesLabelChanges() {
        Note note = note();
        note.trash();
        assertThatThrownBy(() -> note.attachLabel(LabelId.newId())).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(() -> note.detachLabel(LabelId.newId())).isInstanceOf(DomainException.NoteTrashed.class);
        assertThatThrownBy(() -> note.replaceLabels(labels(1))).isInstanceOf(DomainException.NoteTrashed.class);
    }

    @Test
    @DisplayName("the label set is not modifiable from outside the aggregate")
    void labelSetIsEncapsulated() {
        Note note = note();
        note.attachLabel(LabelId.newId());
        assertThatThrownBy(() -> note.labels().add(LabelId.newId())).isInstanceOf(UnsupportedOperationException.class);
    }
}
