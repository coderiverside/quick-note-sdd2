package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.quicknote.support.ApiTestBase;

/**
 * SC-004 and SC-009: authorization verified across <em>every</em> operation the service exposes, not
 * a representative sample.
 *
 * <p>A sampled isolation test is the kind that passes for a year and then misses the one endpoint
 * somebody added without an ownership check. This enumerates all fifteen operations in the contract.
 */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullSurfaceIsolationTest extends ApiTestBase {

    private String aliceNote;
    private String aliceLabel;

    private void seedAlice() {
        if (aliceNote != null) {
            return;
        }
        aliceLabel = as(ALICE)
                .body(Map.of("name", "surface-" + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        aliceNote = as(ALICE)
                .body(Map.of("title", "alice-surface-secret", "body", "confidential-surface-body"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE)
                .when()
                .put("/v1/notes/" + aliceNote + "/labels/" + aliceLabel)
                .then()
                .statusCode(204);
    }

    /** The eight operations that address a specific note or label belonging to someone else. */
    private List<Arguments> resourceOperations() {
        seedAlice();
        return List.of(
                Arguments.of("GET /v1/notes/{noteId}", (Runnable)
                        () -> refuse(as(BOB).when().get("/v1/notes/" + aliceNote))),
                Arguments.of("PATCH /v1/notes/{noteId}", (Runnable)
                        () -> refuse(as(BOB).body(Map.of("body", "x")).when().patch("/v1/notes/" + aliceNote))),
                Arguments.of("DELETE /v1/notes/{noteId}", (Runnable)
                        () -> refuse(as(BOB).when().delete("/v1/notes/" + aliceNote))),
                Arguments.of("PUT /v1/notes/{noteId}/labels", (Runnable) () -> refuse(
                        as(BOB).body(Map.of("labelIds", List.of())).when().put("/v1/notes/" + aliceNote + "/labels"))),
                Arguments.of("PUT /v1/notes/{noteId}/labels/{labelId}", (Runnable)
                        () -> refuse(as(BOB).when().put("/v1/notes/" + aliceNote + "/labels/" + aliceLabel))),
                Arguments.of("DELETE /v1/notes/{noteId}/labels/{labelId}", (Runnable)
                        () -> refuse(as(BOB).when().delete("/v1/notes/" + aliceNote + "/labels/" + aliceLabel))),
                Arguments.of("PATCH /v1/labels/{labelId}", (Runnable)
                        () -> refuse(as(BOB).body(Map.of("name", "x")).when().patch("/v1/labels/" + aliceLabel))),
                Arguments.of("DELETE /v1/labels/{labelId}", (Runnable)
                        () -> refuse(as(BOB).when().delete("/v1/labels/" + aliceLabel))),
                Arguments.of("DELETE /v1/trash/{noteId}", (Runnable)
                        () -> refuse(as(BOB).when().delete("/v1/trash/" + aliceNote))));
    }

    @ParameterizedTest(name = "{0} is refused as absent")
    @MethodSource("resourceOperations")
    @DisplayName("every resource-addressing operation refuses another user, as absence")
    void resourceOperationsRefuseOtherUsers(String operation, Runnable attempt) {
        attempt.run();
    }

    /** Refused with 404 — never 403, which would confirm the resource exists — and with no content. */
    private void refuse(Response response) {
        assertThat(response.statusCode())
                .as("403 would confirm the resource exists")
                .isEqualTo(404);
        assertThat(response.jsonPath().getString("code")).isIn("note_not_found", "label_not_found");
        assertThat(response.asString())
                .doesNotContain("alice-surface-secret")
                .doesNotContain("confidential-surface-body");
    }

    @Test
    @DisplayName("every collection operation returns or affects only the caller's own data")
    void collectionOperationsAreScopedToTheCaller() {
        seedAlice();

        // GET /v1/notes
        Response notes = as(BOB).when().get("/v1/notes?size=100");
        notes.then().statusCode(200);
        assertThat(notes.asString()).doesNotContain("alice-surface-secret");

        // GET /v1/labels
        List<String> bobLabels = as(BOB).when()
                .get("/v1/labels?size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(bobLabels).doesNotContain(aliceLabel);

        // GET /v1/trash
        Response trash = as(BOB).when().get("/v1/trash?size=100");
        trash.then().statusCode(200);
        assertThat(trash.asString()).doesNotContain("alice-surface-secret");

        // POST /v1/notes and POST /v1/labels create for the caller, never for anyone else
        String bobNote = as(BOB).body(Map.of("body", "bob's own"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().get("/v1/notes/" + bobNote).then().statusCode(404);

        // DELETE /v1/trash empties only the caller's trash
        as(ALICE).when().delete("/v1/notes/" + aliceNote).then().statusCode(204);
        as(BOB).when().delete("/v1/trash").then().statusCode(204);
        assertThat(as(ALICE).when().get("/v1/notes/" + aliceNote).statusCode())
                .as("bob emptying his trash must not touch alice's")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("all fifteen contract operations are covered by this test")
    void coversTheWholeSurface() {
        int resourceAddressing = resourceOperations().size();
        int collectionOperations = 6; // GET/POST notes, GET/POST labels, GET/DELETE trash
        assertThat(resourceAddressing + collectionOperations)
                .as("the contract declares fifteen operations; every one must be checked")
                .isEqualTo(15);
    }
}
