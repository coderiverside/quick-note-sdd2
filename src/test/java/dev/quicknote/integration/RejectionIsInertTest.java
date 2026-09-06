package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import jakarta.inject.Inject;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import dev.quicknote.support.ApiTestBase;

/**
 * SC-007: a rejected submission writes nothing at all.
 *
 * <p>Rejection correctness is already covered per endpoint. What this asserts is the property across
 * the whole surface — that a refusal is inert, leaving row counts and existing content untouched. A
 * partial write behind a 4xx is the kind of bug that surfaces as mysterious duplicate rows weeks
 * later.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RejectionIsInertTest extends ApiTestBase {

    @Inject
    AgroalDataSource dataSource;

    private record Snapshot(long notes, long labels, long attachments) {}

    private Snapshot snapshot() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            return new Snapshot(count(statement, "notes"), count(statement, "labels"), count(statement, "note_labels"));
        }
    }

    private static long count(Statement statement, String table) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    @DisplayName("every rejecting operation leaves the store exactly as it was")
    void rejectionsWriteNothing() throws SQLException {
        // A note and a label to attempt invalid changes against.
        String labelName = "inert-" + UUID.randomUUID().toString().substring(0, 8);
        String labelId = as(ALICE)
                .body(Map.of("name", labelName))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String noteId = as(ALICE)
                .body(Map.of("title", "inert original", "body", "original body"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        Map<String, Object> clearBoth = new java.util.HashMap<>();
        clearBoth.put("title", null);
        clearBoth.put("body", null);

        List<UUID> tooManyLabels = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> UUID.randomUUID())
                .toList();

        record Attempt(String name, Supplier<Integer> run) {}
        List<Attempt> attempts = List.of(
                new Attempt("create with no content", () -> asSendingInvalid(ALICE)
                        .body(Map.of())
                        .when()
                        .post("/v1/notes")
                        .statusCode()),
                new Attempt("create with an over-length body", () -> asSendingInvalid(ALICE)
                        .body(Map.of("body", "x".repeat(20_001)))
                        .when()
                        .post("/v1/notes")
                        .statusCode()),
                new Attempt("create with an invalid colour", () -> asSendingInvalid(ALICE)
                        .body(Map.of("body", "b", "color", "chartreuse"))
                        .when()
                        .post("/v1/notes")
                        .statusCode()),
                new Attempt("create referencing an unknown label", () -> asSendingInvalid(ALICE)
                        .body(Map.of("body", "b", "labelIds", List.of(UUID.randomUUID())))
                        .when()
                        .post("/v1/notes")
                        .statusCode()),
                new Attempt("create with an unknown property", () -> asSendingInvalid(ALICE)
                        .body(Map.of("body", "b", "isAdmin", true))
                        .when()
                        .post("/v1/notes")
                        .statusCode()),
                new Attempt("update emptying both fields", () -> asSendingInvalid(ALICE)
                        .body(clearBoth)
                        .when()
                        .patch("/v1/notes/" + noteId)
                        .statusCode()),
                new Attempt("update with an invalid colour", () -> asSendingInvalid(ALICE)
                        .body(Map.of("color", "chartreuse"))
                        .when()
                        .patch("/v1/notes/" + noteId)
                        .statusCode()),
                new Attempt("label create with a blank name", () -> asSendingInvalid(ALICE)
                        .body(Map.of("name", "   "))
                        .when()
                        .post("/v1/labels")
                        .statusCode()),
                // The name is remembered from the create, not read back: a lookup that silently
                // returns null would turn this into a successful create and quietly stop testing
                // anything.
                new Attempt("label create with a duplicate name", () -> asSendingInvalid(ALICE)
                        .body(Map.of("name", labelName.toUpperCase()))
                        .when()
                        .post("/v1/labels")
                        .statusCode()),
                new Attempt("label rename to blank", () -> asSendingInvalid(ALICE)
                        .body(Map.of("name", " "))
                        .when()
                        .patch("/v1/labels/" + labelId)
                        .statusCode()),
                new Attempt("attach an unknown label", () -> asSendingInvalid(ALICE)
                        .when()
                        .put("/v1/notes/" + noteId + "/labels/" + UUID.randomUUID())
                        .statusCode()),
                new Attempt("replace with more labels than permitted", () -> asSendingInvalid(ALICE)
                        .body(Map.of("labelIds", tooManyLabels))
                        .when()
                        .put("/v1/notes/" + noteId + "/labels")
                        .statusCode()));

        Snapshot before = snapshot();
        List<String> unexpected = new ArrayList<>();

        for (Attempt attempt : attempts) {
            int status = attempt.run().get();
            if (status < 400 || status >= 500) {
                unexpected.add(attempt.name() + " returned " + status);
            }
        }

        assertThat(unexpected)
                .as("every one of these must be refused, and refused deliberately")
                .isEmpty();

        Snapshot after = snapshot();
        assertThat(after).as("a refused request must write nothing").isEqualTo(before);

        // And the note it was aimed at is untouched, content and all.
        as(ALICE)
                .when()
                .get("/v1/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("title", org.hamcrest.Matchers.is("inert original"))
                .body("body", org.hamcrest.Matchers.is("original body"))
                .body("color", org.hamcrest.Matchers.is("default"));
    }
}
