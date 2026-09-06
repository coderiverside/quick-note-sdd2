package dev.quicknote.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.quicknote.notes.domain.NoteColor;
import dev.quicknote.notes.domain.NoteState;
import dev.quicknote.notes.domain.UserId;

class ValueTypeTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "default",
                "red",
                "orange",
                "yellow",
                "green",
                "teal",
                "blue",
                "dark_blue",
                "purple",
                "pink",
                "brown"
            })
    @DisplayName("every palette value parses from its wire form")
    void paletteParses(String wire) {
        assertThat(NoteColor.fromWire(wire))
                .isPresent()
                .get()
                .extracting(NoteColor::wireName)
                .isEqualTo(wire);
    }

    @Test
    @DisplayName("the palette is exactly eleven values")
    void paletteIsClosed() {
        assertThat(NoteColor.values()).hasSize(11);
    }

    @Test
    @DisplayName("parsing is case- and whitespace-insensitive")
    void paletteParsingIsLenientOnCasing() {
        assertThat(NoteColor.fromWire("  DARK_BLUE ")).contains(NoteColor.DARK_BLUE);
    }

    @Test
    @DisplayName("an unknown colour is rejected, not defaulted")
    void unknownColourRejected() {
        assertThat(NoteColor.fromWire("chartreuse")).isEmpty();
        assertThat(NoteColor.fromWire(null)).isEmpty();
        assertThat(NoteColor.fromWire("")).isEmpty();
    }

    @Test
    @DisplayName("permitted values are listed for the error message")
    void permittedValuesListed() {
        assertThat(NoteColor.permittedValues()).contains("default", "dark_blue", "brown");
    }

    @Test
    @DisplayName("a note is in exactly one of three states")
    void statesAreExclusive() {
        assertThat(NoteState.values()).containsExactly(NoteState.ACTIVE, NoteState.ARCHIVED, NoteState.TRASHED);
        assertThat(NoteState.ACTIVE.isArchived()).isFalse();
        assertThat(NoteState.ACTIVE.isTrashed()).isFalse();
        assertThat(NoteState.ARCHIVED.isArchived()).isTrue();
        assertThat(NoteState.ARCHIVED.isTrashed()).isFalse();
        assertThat(NoteState.TRASHED.isTrashed()).isTrue();
        assertThat(NoteState.TRASHED.isArchived()).isFalse();
    }

    @Test
    @DisplayName("a blank user identity is refused")
    void userIdMustNotBeBlank() {
        assertThatThrownBy(() -> UserId.of("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserId.of(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(UserId.of("sub-123").value()).isEqualTo("sub-123");
    }

    @Test
    @DisplayName("identifiers are distinct types, not interchangeable UUIDs")
    void identifiersAreDistinctTypes() {
        var noteId = dev.quicknote.notes.domain.NoteId.newId();
        var labelId = dev.quicknote.notes.domain.LabelId.newId();
        assertThat(noteId.value()).isNotNull();
        assertThat(labelId.value()).isNotNull();
        assertThat((Object) noteId).isNotEqualTo(labelId);
        assertThat(Optional.of(noteId.toString())).contains(noteId.value().toString());
    }
}
