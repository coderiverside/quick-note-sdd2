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
 * The constitution treats an unindexed access path as a defect, not a tuning opportunity. This asks
 * PostgreSQL directly rather than trusting that an index exists.
 */
@QuarkusTest
class NoteQueryPlanTest {

    @Inject
    AgroalDataSource dataSource;

    private String explain(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
            StringBuilder plan = new StringBuilder();
            while (rs.next()) {
                plan.append(rs.getString(1)).append('\n');
            }
            return plan.toString();
        }
    }

    private static final String OWNER = "plan-test-owner";
    private static final String LISTING_QUERY = "SELECT id FROM notes WHERE owner_id = '" + OWNER
            + "' AND state = 'ACTIVE' ORDER BY pinned DESC, updated_at DESC, id DESC LIMIT 25 OFFSET 0";

    /**
     * The planner picks a sequential scan on a tiny table no matter how good the index is, so the
     * test seeds a realistic volume first. Otherwise it would assert nothing.
     */
    private void seedAndAnalyze() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "INSERT INTO notes (id, owner_id, title, body, color, pinned, state, created_at, updated_at,"
                            + " version) SELECT gen_random_uuid(), '" + OWNER + "', 'plan ' || g, 'body ' || g,"
                            + " 'default', false, 'ACTIVE', now() - (g || ' seconds')::interval,"
                            + " now() - (g || ' seconds')::interval, 0 FROM generate_series(1, 2000) g");
            statement.execute("ANALYZE notes");
        }
    }

    @Test
    @DisplayName("the ordered listing is served by the composite index, not a sequential scan")
    void listingUsesTheCompositeIndex() throws SQLException {
        seedAndAnalyze();
        String plan = explain(LISTING_QUERY);
        assertThat(plan)
                .as("an unindexed access path is a defect, not a tuning opportunity:%n%s", plan)
                .contains("ix_notes_owner_listing")
                .doesNotContain("Seq Scan");
    }

    @Test
    @DisplayName("the index covers the ordering too, so no sort is needed")
    void indexCoversOrdering() throws SQLException {
        seedAndAnalyze();
        String plan = explain(LISTING_QUERY);
        assertThat(plan)
                .as("a Sort node means the index order does not match the query order:%n%s", plan)
                .doesNotContain("Sort Key");
    }
}
