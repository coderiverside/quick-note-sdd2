package dev.quicknote.contract;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** The trash endpoints, validated against the authored contract. */
@QuarkusTest
class TrashContractTest extends ApiTestBase {

    private String trashedNote() {
        String id = as(BOB).body(Map.of("title", "trashed", "body", "b"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(BOB).when().delete("/v1/notes/" + id).then().statusCode(204);
        return id;
    }

    @Test
    @DisplayName("the trash listing returns a conformant page envelope")
    void listEnvelope() {
        trashedNote();
        as(BOB).when()
                .get("/v1/trash")
                .then()
                .statusCode(200)
                .body("items", notNullValue())
                .body("page", is(0))
                .body("size", is(25))
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("items[0].trashed", is(true))
                .body("items[0].trashedAt", notNullValue());
    }

    @Test
    @DisplayName("purging a trashed note answers 204 and is irreversible")
    void purgeIsIrreversible() {
        String id = trashedNote();
        as(BOB).when().delete("/v1/trash/" + id).then().statusCode(204);
        as(BOB).when().get("/v1/notes/" + id).then().statusCode(404).body("code", is("note_not_found"));
        as(BOB).when().delete("/v1/trash/" + id).then().statusCode(404);
    }

    @Test
    @DisplayName("a note that is not in the trash cannot be purged")
    void liveNoteCannotBePurged() {
        String id = as(BOB).body(Map.of("body", "still alive"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        as(BOB).when().delete("/v1/trash/" + id).then().statusCode(404).body("code", is("note_not_found"));

        as(BOB).when().get("/v1/notes/" + id).then().statusCode(200).body("trashed", is(false));
    }

    @Test
    @DisplayName("emptying the trash answers 204")
    void emptyAnswers204() {
        trashedNote();
        as(BOB).when().delete("/v1/trash").then().statusCode(204);
        as(BOB).when().get("/v1/trash").then().statusCode(200).body("totalElements", is(0));
    }

    @Test
    @DisplayName("emptying an already-empty trash is still 204")
    void emptyIsIdempotent() {
        as(BOB).when().delete("/v1/trash").then().statusCode(204);
        as(BOB).when().delete("/v1/trash").then().statusCode(204);
    }

    @Test
    @DisplayName("a malformed identifier is rejected before any lookup")
    void malformedIdentifier() {
        as(BOB).when().delete("/v1/trash/not-a-uuid").then().statusCode(400).body("code", is("malformed_identifier"));
    }

    @Test
    @DisplayName("another user's trashed note cannot be purged and is reported as absent")
    void isolation() {
        String id = trashedNote();
        as(ALICE).when().delete("/v1/trash/" + id).then().statusCode(404).body("code", is("note_not_found"));
        as(BOB).when().get("/v1/notes/" + id).then().statusCode(200);
    }

    @Test
    @DisplayName("purging a note that never existed is reported as absent")
    void unknownNote() {
        as(BOB).when().delete("/v1/trash/" + UUID.randomUUID()).then().statusCode(404);
    }
}
