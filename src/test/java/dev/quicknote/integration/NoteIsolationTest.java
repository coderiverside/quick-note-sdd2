package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * FR-007 and FR-008: notes are scoped to their owner, and another user's note is reported exactly as
 * a note that does not exist.
 *
 * <p>The distinction matters: a 403 would confirm the note exists.
 */
@QuarkusTest
class NoteIsolationTest extends ApiTestBase {

    private String aliceNote() {
        return as(ALICE)
                .body(Map.of("title", "alice private", "body", "secret"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("bob cannot read alice's note")
    void cannotRead() {
        String id = aliceNote();
        Response response = as(BOB).when().get("/v1/notes/" + id);
        response.then().statusCode(404).body("code", is("note_not_found"));
        assertThat(response.asString()).doesNotContain("alice private").doesNotContain("secret");
    }

    @Test
    @DisplayName("bob cannot update alice's note")
    void cannotUpdate() {
        String id = aliceNote();
        as(BOB).body(Map.of("title", "hijacked"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(404)
                .body("code", is("note_not_found"));

        as(ALICE).when().get("/v1/notes/" + id).then().statusCode(200).body("title", is("alice private"));
    }

    @Test
    @DisplayName("bob cannot delete alice's note")
    void cannotDelete() {
        String id = aliceNote();
        as(BOB).when().delete("/v1/notes/" + id).then().statusCode(404);
        as(ALICE).when().get("/v1/notes/" + id).then().statusCode(200).body("trashed", is(false));
    }

    @Test
    @DisplayName("refusal is indistinguishable from absence")
    void refusalLooksLikeAbsence() {
        String existing = aliceNote();
        Response othersNote = as(BOB).when().get("/v1/notes/" + existing);
        Response nonexistent = as(BOB).when().get("/v1/notes/" + UUID.randomUUID());

        assertThat(othersNote.statusCode()).isEqualTo(nonexistent.statusCode()).isEqualTo(404);
        assertThat(othersNote.jsonPath().getString("code"))
                .isEqualTo(nonexistent.jsonPath().getString("code"));
        assertThat(othersNote.jsonPath().getString("detail"))
                .isEqualTo(nonexistent.jsonPath().getString("detail"));
        assertThat(othersNote.statusCode())
                .as("403 would confirm the note exists")
                .isNotEqualTo(403);
    }

    @Test
    @DisplayName("a listing never contains another user's notes")
    void listingIsScopedToOwner() {
        aliceNote();
        Response bobsList = as(BOB).when().get("/v1/notes?size=100");
        bobsList.then().statusCode(200);
        assertThat(bobsList.asString()).doesNotContain("alice private").doesNotContain("secret");
    }
}
