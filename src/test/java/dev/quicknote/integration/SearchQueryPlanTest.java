package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A leading-wildcard ILIKE cannot use a B-tree at all. Without trigram indexes, search is a
 * sequential scan at every table size, forever — the failure mode the constitution's index rule
 * exists to prevent.
 *
 * <p>What this test asserts is that such an index <em>exists and applies</em> to the query the
 * service actually issues. It deliberately does not assert that the planner chooses it: on a small
 * table a sequential scan really is cheaper, and a test that demanded otherwise would be asserting
 * that PostgreSQL is wrong. Disabling {@code enable_seqscan} asks the narrower and more useful
 * question — when scanning is not an option, is there an index that can serve this predicate?
 */
@QuarkusTest
class SearchQueryPlanTest {

    private static final String OWNER = "search-plan-owner";

    /** Present on 3 of 3,000 rows, so the predicate is selective rather than a match-everything. */
    private static final String RARE_TERM = "zebracode";

    @Inject
    AgroalDataSource dataSource;

    private void seedAndAnalyze() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM notes WHERE owner_id = '" + OWNER + "'");
            statement.executeUpdate("INSERT INTO notes (id, owner_id, title, body, color, pinned, state, created_at,"
                    + " updated_at, version) SELECT gen_random_uuid(), '" + OWNER + "', 'plan title ' || g,"
                    + " 'searchable body number ' || g || CASE WHEN g % 1000 = 0 THEN ' " + RARE_TERM + "' ELSE ''"
                    + " END, 'default', false, 'ACTIVE', now(), now(), 0 FROM generate_series(1, 3000) g");
            statement.execute("ANALYZE notes");
        }
    }

    private String explainWithoutSeqScan(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET enable_seqscan = off");
            try (ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
                StringBuilder plan = new StringBuilder();
                while (rs.next()) {
                    plan.append(rs.getString(1)).append('\n');
                }
                return plan.toString();
            }
        }
    }

    @Test
    @DisplayName("substring search can be served by the trigram indexes")
    void searchUsesTrigramIndexes() throws SQLException {
        seedAndAnalyze();
        // Exactly the predicate PanacheNoteRepository issues: bare columns, no COALESCE. Wrapping
        // either column in a function would make these indexes unusable, which is the regression
        // this test is here to catch.
        String plan = explainWithoutSeqScan(
                "SELECT id FROM notes WHERE title ILIKE '%" + RARE_TERM + "%' OR body ILIKE '%" + RARE_TERM + "%'");

        assertThat(plan)
                .as("no trigram index applies, so every search is a sequential scan:%n%s", plan)
                .containsAnyOf("ix_notes_title_trgm", "ix_notes_body_trgm");
    }

    @Test
    @DisplayName("the retention sweep is served by the partial index on trashed rows")
    void retentionSweepIsIndexed() throws SQLException {
        seedAndAnalyze();
        String plan =
                explainWithoutSeqScan("SELECT id FROM notes WHERE state = 'TRASHED' AND trashed_at < now() LIMIT 500");
        assertThat(plan)
                .as("the nightly sweep must not scan the whole table:%n%s", plan)
                .contains("ix_notes_trashed_at");
    }

    @Test
    @DisplayName("the owner listing index applies to the ordered listing")
    void listingIndexApplies() throws SQLException {
        seedAndAnalyze();
        String plan = explainWithoutSeqScan("SELECT id FROM notes WHERE owner_id = '" + OWNER
                + "' AND state = 'ACTIVE' ORDER BY pinned DESC, updated_at DESC, id DESC LIMIT 25");
        assertThat(plan).as("%s", plan).contains("ix_notes_owner_listing");
    }
}
