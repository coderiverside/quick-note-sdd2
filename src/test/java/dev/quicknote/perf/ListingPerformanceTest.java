package dev.quicknote.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * SC-001 and SC-002, measured rather than assumed.
 *
 * <p>Runs under the {@code perf} profile only: it seeds a thousand notes and the numbers are
 * meaningless on a contended CI box, so it is not part of the ordinary gate.
 */
@QuarkusTest
class ListingPerformanceTest extends ApiTestBase {

    private static final int SEEDED_NOTES = 1_000;
    private static final int SAMPLES = 50;

    @Inject
    AgroalDataSource dataSource;

    private String seedOwner() throws SQLException {
        // Seeded through SQL rather than the API: a thousand HTTP round trips would measure the test.
        String owner = as(ALICE)
                .body(Map.of("body", "anchor"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO notes (id, owner_id, title, body, color, pinned, state, created_at,"
                    + " updated_at, version) SELECT gen_random_uuid(), (SELECT owner_id FROM notes WHERE id = '"
                    + owner + "'), 'perf title ' || g, 'perf body ' || g, 'default', false, 'ACTIVE',"
                    + " now() - (g || ' seconds')::interval, now() - (g || ' seconds')::interval, 0"
                    + " FROM generate_series(1, " + SEEDED_NOTES + ") g");
            statement.execute("ANALYZE notes");
        }
        return owner;
    }

    private static long p95(List<Long> samplesMillis) {
        List<Long> sorted = new ArrayList<>(samplesMillis);
        sorted.sort(Long::compareTo);
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    }

    @Test
    @DisplayName("the first page of a 1,000-note collection returns well inside one second at p95")
    void firstPageLatency() throws SQLException {
        seedOwner();

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            Instant start = Instant.now();
            as(ALICE).when().get("/v1/notes?size=25").then().statusCode(200);
            samples.add(Duration.between(start, Instant.now()).toMillis());
        }

        assertThat(p95(samples))
                .as("SC-002: first page under 1 s at p95, samples=%s", samples)
                .isLessThan(1_000);
    }

    @Test
    @DisplayName("a single-note read stays inside the 300 ms constitutional budget at p95")
    void singleNoteLatency() throws SQLException {
        String id = seedOwner();

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            Instant start = Instant.now();
            as(ALICE).when().get("/v1/notes/" + id).then().statusCode(200);
            samples.add(Duration.between(start, Instant.now()).toMillis());
        }

        assertThat(p95(samples))
                .as("p95 read under 300 ms, samples=%s", samples)
                .isLessThan(300);
    }

    @Test
    @DisplayName("capturing a note and receiving its confirmation is well under five seconds")
    void captureLatency() {
        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            Instant start = Instant.now();
            as(ALICE)
                    .body(Map.of("title", "capture " + i, "body", "b"))
                    .when()
                    .post("/v1/notes")
                    .then()
                    .statusCode(201);
            samples.add(Duration.between(start, Instant.now()).toMillis());
        }

        assertThat(p95(samples))
                .as("SC-001: capture confirmed under 5 s, samples=%s", samples)
                .isLessThan(5_000);
    }

    @Test
    @DisplayName("search over a 1,000-note collection stays inside the read budget")
    void searchLatency() throws SQLException {
        seedOwner();

        List<Long> samples = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            Instant start = Instant.now();
            as(ALICE)
                    .queryParam("q", "perf body " + i)
                    .when()
                    .get("/v1/notes")
                    .then()
                    .statusCode(200);
            samples.add(Duration.between(start, Instant.now()).toMillis());
        }

        assertThat(p95(samples)).as("search p95 under 1 s, samples=%s", samples).isLessThan(1_000);
    }
}
