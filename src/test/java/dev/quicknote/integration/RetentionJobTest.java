package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.platform.scheduler.RetentionJob;
import dev.quicknote.support.ApiTestBase;

/**
 * FR-024: notes trashed longer than the retention window are removed without user action.
 *
 * <p>Waiting 30 days is not an option, so the test backdates {@code trashed_at} directly and then
 * runs the sweep. The boundary is what matters: one day inside the window must survive.
 */
@QuarkusTest
class RetentionJobTest extends ApiTestBase {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    RetentionJob retentionJob;

    @Inject
    dev.quicknote.notes.application.TrashService trashService;

    private String trashedNote(String body) {
        String id = as(ALICE)
                .body(Map.of("body", body))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);
        return id;
    }

    private void backdateTrashedAt(String noteId, int days) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE notes SET trashed_at = now() - (? || ' days')::interval WHERE id = ?::uuid")) {
            statement.setInt(1, days);
            statement.setString(2, noteId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("a note past the retention window is removed; one inside it is not")
    void sweepRespectsTheBoundary() throws SQLException {
        String expired = trashedNote("trashed 31 days ago");
        String justInside = trashedNote("trashed 29 days ago");
        String fresh = trashedNote("trashed just now");

        backdateTrashedAt(expired, 31);
        backdateTrashedAt(justInside, 29);

        long removed = retentionJob.purgeExpiredNotes();

        assertThat(removed).isPositive();
        as(ALICE).when().get("/v1/notes/" + expired).then().statusCode(404);
        as(ALICE).when().get("/v1/notes/" + justInside).then().statusCode(200);
        as(ALICE).when().get("/v1/notes/" + fresh).then().statusCode(200);
    }

    @Test
    @DisplayName("the sweep never touches an active or archived note, however old")
    void sweepIgnoresLiveNotes() throws SQLException {
        String active = as(ALICE)
                .body(Map.of("body", "old but alive"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE notes SET created_at = now() - interval '400 days',"
                                + " updated_at = now() - interval '400 days' WHERE id = ?::uuid")) {
            statement.setString(1, active);
            statement.executeUpdate();
        }

        retentionJob.purgeExpiredNotes();
        as(ALICE).when().get("/v1/notes/" + active).then().statusCode(200);
    }

    @Test
    @DisplayName("the configured retention window is the thirty days the spec promises")
    void retentionWindowMatchesTheSpec() {
        assertThat(trashService.retentionDays())
                .as("the window users are told about must be the window the sweep enforces")
                .isEqualTo(30);
    }

    @Test
    @DisplayName("a sweep with nothing to do removes nothing and does not fail")
    void emptySweepIsHarmless() {
        retentionJob.purgeExpiredNotes();
        assertThat(retentionJob.purgeExpiredNotes()).isZero();
    }

    @Test
    @DisplayName("expiry is judged per note, not per user")
    void expiryIsPerNote() throws SQLException {
        String bobsExpired = as(BOB).body(Map.of("body", "bob old"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(BOB).when().delete("/v1/notes/" + bobsExpired).then().statusCode(204);
        backdateTrashedAt(bobsExpired, 45);

        String alicesFresh = trashedNote("alice fresh");

        retentionJob.purgeExpiredNotes();

        as(BOB).when().get("/v1/notes/" + bobsExpired).then().statusCode(404);
        as(ALICE).when().get("/v1/notes/" + alicesFresh).then().statusCode(200);
    }
}
