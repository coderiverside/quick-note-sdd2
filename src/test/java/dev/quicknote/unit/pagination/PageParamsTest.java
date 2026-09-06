package dev.quicknote.unit.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.notes.domain.Page;
import dev.quicknote.shared.pagination.PageParams;
import dev.quicknote.shared.problem.DomainException;

class PageParamsTest {

    @Test
    @DisplayName("defaults to page 0, size 25")
    void defaults() {
        PageParams params = PageParams.of(null, null);
        assertThat(params.page()).isZero();
        assertThat(params.size()).isEqualTo(25);
        assertThat(params.offset()).isZero();
    }

    @Test
    @DisplayName("a size above the maximum is rejected, never clamped")
    void rejectsOversizedPage() {
        assertThatThrownBy(() -> PageParams.of(0, 101))
                .isInstanceOf(DomainException.ValidationFailed.class)
                .satisfies(e -> {
                    var failure = (DomainException.ValidationFailed) e;
                    assertThat(failure.error().status()).isEqualTo(400);
                    assertThat(failure.fieldErrors()).singleElement().satisfies(fe -> {
                        assertThat(fe.field()).isEqualTo("size");
                        assertThat(fe.message()).contains("100");
                    });
                });
        assertThat(PageParams.of(0, 100).size()).isEqualTo(100);
    }

    @Test
    @DisplayName("a negative page and a zero size are rejected")
    void rejectsOutOfRange() {
        assertThatThrownBy(() -> PageParams.of(-1, 25)).isInstanceOf(DomainException.ValidationFailed.class);
        assertThatThrownBy(() -> PageParams.of(0, 0)).isInstanceOf(DomainException.ValidationFailed.class);
    }

    @Test
    @DisplayName("both offending fields are reported together, not one at a time")
    void reportsAllOffendingFields() {
        assertThatThrownBy(() -> PageParams.of(-1, 500))
                .satisfies(e -> assertThat(((DomainException.ValidationFailed) e).fieldErrors())
                        .extracting("field")
                        .containsExactlyInAnyOrder("page", "size"));
    }

    @Test
    @DisplayName("offset follows from page and size")
    void offsetIsDerived() {
        assertThat(PageParams.of(3, 20).offset()).isEqualTo(60);
    }

    @Test
    @DisplayName("page arithmetic reports total pages and whether another page follows")
    void pageArithmetic() {
        Page<String> page = new Page<>(List.of("a", "b"), 0, 2, 5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();

        Page<String> last = new Page<>(List.of("e"), 2, 2, 5);
        assertThat(last.hasNext()).isFalse();

        Page<String> empty = new Page<>(List.of(), 0, 25, 0);
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.hasNext()).isFalse();
        assertThat(empty.items()).isEmpty();
    }

    @Test
    @DisplayName("mapping a page preserves its pagination metadata")
    void mappingPreservesMetadata() {
        Page<Integer> mapped = new Page<>(List.of("a", "bb"), 1, 2, 7).map(String::length);
        assertThat(mapped.items()).containsExactly(1, 2);
        assertThat(mapped.page()).isEqualTo(1);
        assertThat(mapped.totalElements()).isEqualTo(7);
    }
}
