package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The database refuses an illegal row even when asked directly.
 *
 * <p>The domain keeps the model correct when called through the application; these constraints are
 * what guarantee no bad row can exist at all — including one written by a future code path that
 * forgets to ask.
 */
@QuarkusTest
class NoteConstraintTest {

    @Inject
    AgroalDataSource dataSource;

    private void insert(String columns, String values) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO notes (id, owner_id, created_at, updated_at, " + columns
                    + ") VALUES ('"
                    + UUID.randomUUID() + "', 'constraint-test', '" + Instant.now() + "', '" + Instant.now() + "', "
                    + values + ")");
        }
    }

    @Test
    @DisplayName("a note with neither title nor body is refused by the database")
    void contentRequired() {
        assertThatThrownBy(() -> insert("title, body", "NULL, NULL"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_content");
    }

    @Test
    @DisplayName("a pinned note that is not active is refused by the database")
    void pinnedImpliesActive() {
        assertThatThrownBy(() -> insert("body, pinned, state", "'b', true, 'ARCHIVED'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_pinned_active");

        assertThatThrownBy(() -> insert("body, pinned, state, trashed_at", "'b', true, 'TRASHED', now()"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_pinned_active");
    }

    @Test
    @DisplayName("trashed_at is set exactly when the note is trashed")
    void trashedAtMatchesState() {
        assertThatThrownBy(() -> insert("body, state, trashed_at", "'b', 'TRASHED', NULL"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_trashed_at");

        assertThatThrownBy(() -> insert("body, state, trashed_at", "'b', 'ACTIVE', now()"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_trashed_at");
    }

    @Test
    @DisplayName("an unknown state value is refused")
    void stateIsAClosedSet() {
        assertThatThrownBy(() -> insert("body, state", "'b', 'LIMBO'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_state");
    }

    @Test
    @DisplayName("an over-length body is refused even when written directly")
    void bodyLengthEnforced() {
        assertThatThrownBy(() -> insert("body", "repeat('x', 20001)"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ck_notes_body_len");
    }
}
