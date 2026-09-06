package dev.quicknote.unit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.notes.domain.Label;
import dev.quicknote.notes.domain.UserId;
import dev.quicknote.shared.problem.DomainException;

class LabelTest {

    private static final UserId OWNER = UserId.of("owner-1");

    @Test
    @DisplayName("the name is trimmed but its casing is preserved")
    void trimsButPreservesCasing() {
        assertThat(Label.create(OWNER, "  Work Notes  ").name()).isEqualTo("Work Notes");
    }

    @Test
    @DisplayName("a blank name is refused")
    void blankRefused() {
        assertThatThrownBy(() -> Label.create(OWNER, "   ")).isInstanceOf(DomainException.ValidationFailed.class);
        assertThatThrownBy(() -> Label.create(OWNER, "")).isInstanceOf(DomainException.ValidationFailed.class);
        assertThatThrownBy(() -> Label.create(OWNER, null)).isInstanceOf(DomainException.ValidationFailed.class);
    }

    @Test
    @DisplayName("an over-length name is refused, naming the field")
    void overLongRefused() {
        assertThat(Label.create(OWNER, "a".repeat(50)).name()).hasSize(50);
        assertThatThrownBy(() -> Label.create(OWNER, "a".repeat(51)))
                .isInstanceOf(DomainException.ValidationFailed.class)
                .satisfies(e -> assertThat(((DomainException.ValidationFailed) e).fieldErrors())
                        .singleElement()
                        .satisfies(fe -> assertThat(fe.field()).isEqualTo("name")));
    }

    @Test
    @DisplayName("uniqueness compares case-insensitively and ignores surrounding whitespace")
    void comparisonKeyIgnoresCaseAndWhitespace() {
        String expected = "work";
        assertThat(Label.create(OWNER, "work").comparisonKey()).isEqualTo(expected);
        assertThat(Label.create(OWNER, "WORK").comparisonKey()).isEqualTo(expected);
        assertThat(Label.create(OWNER, "  Work  ").comparisonKey()).isEqualTo(expected);
        assertThat(Label.create(OWNER, "WoRk").comparisonKey()).isEqualTo(expected);
    }

    @Test
    @DisplayName("distinct names have distinct keys")
    void distinctNamesDiffer() {
        assertThat(Label.create(OWNER, "work").comparisonKey())
                .isNotEqualTo(Label.create(OWNER, "workshop").comparisonKey());
    }

    @Test
    @DisplayName("renaming applies the same rules as creating")
    void renameValidates() {
        Label label = Label.create(OWNER, "work");
        label.rename("  Personal  ");
        assertThat(label.name()).isEqualTo("Personal");
        assertThatThrownBy(() -> label.rename("  ")).isInstanceOf(DomainException.ValidationFailed.class);
        assertThatThrownBy(() -> label.rename("a".repeat(51))).isInstanceOf(DomainException.ValidationFailed.class);
    }

    @Test
    @DisplayName("ownership is fixed at creation")
    void ownershipIsImmutable() {
        Label label = Label.create(OWNER, "work");
        assertThat(label.isOwnedBy(OWNER)).isTrue();
        assertThat(label.isOwnedBy(UserId.of("other"))).isFalse();
    }
}
