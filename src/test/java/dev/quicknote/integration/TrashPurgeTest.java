package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-025: permanent deletion is irreversible, and it touches nothing else. */
@QuarkusTest
class TrashPurgeTest extends ApiTestBase {

    private String note(String body) {
        return as(BOB).body(Map.of("body", body))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String trashed(String body) {
        String id = note(body);
        as(BOB).when().delete("/v1/notes/" + id).then().statusCode(204);
        return id;
    }

    @Test
    @DisplayName("purging one note leaves every other note alone")
    void purgeIsTargeted() {
        String doomed = trashed("doomed");
        String survivor = trashed("survivor");
        String active = note("active");

        as(BOB).when().delete("/v1/trash/" + doomed).then().statusCode(204);

        as(BOB).when().get("/v1/notes/" + doomed).then().statusCode(404);
        as(BOB).when().get("/v1/notes/" + survivor).then().statusCode(200).body("trashed", is(true));
        as(BOB).when().get("/v1/notes/" + active).then().statusCode(200).body("trashed", is(false));
    }

    @Test
    @DisplayName("emptying the trash removes every trashed note and no other")
    void emptyIsScopedToTheTrash() {
        String firstTrashed = trashed("empty-1");
        String secondTrashed = trashed("empty-2");
        String active = note("empty-active");
        String archivedId = note("empty-archived");
        as(BOB).body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + archivedId)
                .then()
                .statusCode(200);

        as(BOB).when().delete("/v1/trash").then().statusCode(204);

        as(BOB).when().get("/v1/notes/" + firstTrashed).then().statusCode(404);
        as(BOB).when().get("/v1/notes/" + secondTrashed).then().statusCode(404);
        as(BOB).when().get("/v1/notes/" + active).then().statusCode(200);
        as(BOB).when().get("/v1/notes/" + archivedId).then().statusCode(200).body("archived", is(true));
    }

    @Test
    @DisplayName("emptying the trash deletes no label")
    void emptyDeletesNoLabel() {
        String labelId = as(BOB).body(
                        Map.of("name", "keep-" + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String id = note("labelled then trashed");
        as(BOB).when().put("/v1/notes/" + id + "/labels/" + labelId).then().statusCode(204);
        as(BOB).when().delete("/v1/notes/" + id).then().statusCode(204);
        as(BOB).when().delete("/v1/trash").then().statusCode(204);

        List<String> labels =
                as(BOB).when().get("/v1/labels?size=100").then().extract().path("items.id");
        assertThat(labels).as("the label outlives the note that carried it").contains(labelId);
    }

    @Test
    @DisplayName("a purged note cannot be restored")
    void purgedNoteCannotBeRestored() {
        String id = trashed("gone for good");
        as(BOB).when().delete("/v1/trash/" + id).then().statusCode(204);

        as(BOB).body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(404)
                .body("code", is("note_not_found"));
    }

    @Test
    @DisplayName("one user emptying the trash does not touch another's")
    void emptyIsScopedToOwner() {
        String bobs = trashed("bob's trash");
        String alices = as(ALICE)
                .body(Map.of("body", "alice's trash"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().delete("/v1/notes/" + alices).then().statusCode(204);

        as(BOB).when().delete("/v1/trash").then().statusCode(204);

        as(BOB).when().get("/v1/notes/" + bobs).then().statusCode(404);
        as(ALICE).when().get("/v1/notes/" + alices).then().statusCode(200).body("trashed", is(true));
    }
}
