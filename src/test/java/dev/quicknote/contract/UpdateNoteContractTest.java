package dev.quicknote.contract;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** {@code PATCH} and {@code DELETE} on a single note. */
@QuarkusTest
class UpdateNoteContractTest extends ApiTestBase {

    private String create(Map<String, Object> body) {
        return as(ALICE)
                .body(body)
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("a partial update changes only what it names")
    void partialUpdate() {
        String id = create(Map.of("title", "original", "body", "keep me"));
        as(ALICE)
                .body(Map.of("title", "changed"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("changed"))
                .body("body", is("keep me"));
    }

    @Test
    @DisplayName("an explicit null clears a field")
    void explicitNullClears() {
        String id = create(Map.of("title", "has title", "body", "has body"));
        Map<String, Object> clearTitle = new HashMap<>();
        clearTitle.put("title", null);

        as(ALICE)
                .body(clearTitle)
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is(emptyOrNullString()))
                .body("body", is("has body"));
    }

    @Test
    @DisplayName("an update that would empty both title and body is refused, and changes nothing")
    void cannotEmptyBoth() {
        String id = create(Map.of("title", "only title"));
        Map<String, Object> clearBoth = new HashMap<>();
        clearBoth.put("title", null);
        clearBoth.put("body", null);

        as(ALICE)
                .body(clearBoth)
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(400)
                .body("code", is("note_content_required"));

        as(ALICE).when().get("/v1/notes/" + id).then().statusCode(200).body("title", is("only title"));
    }

    @Test
    @DisplayName("the last-updated timestamp advances on a change")
    void updatedAtAdvances() {
        String id = create(Map.of("body", "b"));
        String before = as(ALICE).when().get("/v1/notes/" + id).then().extract().path("updatedAt");
        String after = as(ALICE)
                .body(Map.of("body", "b2"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .extract()
                .path("updatedAt");
        org.assertj.core.api.Assertions.assertThat(java.time.Instant.parse(after))
                .isAfter(java.time.Instant.parse(before));
    }

    @Test
    @DisplayName("an empty patch body is rejected as malformed")
    void emptyPatchRejected() {
        String id = create(Map.of("body", "b"));
        asSendingInvalid(ALICE)
                .body(Collections.emptyMap())
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(400)
                .body("code", is("malformed_request"));
    }

    @Test
    @DisplayName("deleting moves the note to the trash and answers 204")
    void deleteTrashes() {
        String id = create(Map.of("body", "b"));
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        as(ALICE)
                .when()
                .get("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("trashed", is(true))
                .body("trashedAt", is(org.hamcrest.Matchers.notNullValue()))
                .body("pinned", is(false));
    }

    @Test
    @DisplayName("deleting is idempotent and does not disturb the original trash time")
    void repeatedDeleteIsInert() {
        String id = create(Map.of("body", "b"));
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);
        String first = as(ALICE).when().get("/v1/notes/" + id).then().extract().path("trashedAt");
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);
        as(ALICE).when().get("/v1/notes/" + id).then().body("trashedAt", is(first));
    }

    @Test
    @DisplayName("a trashed note refuses content edits until it is restored")
    void trashedNoteRefusesEdits() {
        String id = create(Map.of("body", "b"));
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        as(ALICE)
                .body(Map.of("body", "changed"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(409)
                .body("code", is("note_trashed"));
    }

    @Test
    @DisplayName("updating a note that does not exist is reported as absent")
    void updateUnknownNote() {
        as(ALICE)
                .body(Map.of("body", "x"))
                .when()
                .patch("/v1/notes/" + java.util.UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("note_not_found"));
    }
}
