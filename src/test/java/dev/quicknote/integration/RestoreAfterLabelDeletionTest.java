package dev.quicknote.integration;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * The spec's edge case: a label is deleted while a note carrying it sits in the trash.
 *
 * <p>Restoring must succeed and simply come back without that label — not fail, and not resurrect a
 * label that no longer exists.
 */
@QuarkusTest
class RestoreAfterLabelDeletionTest extends ApiTestBase {

    private String label(String prefix) {
        return as(ALICE)
                .body(Map.of("name", prefix + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("restoring succeeds without the label that was deleted meanwhile")
    void restoreAfterLabelDeleted() {
        String doomedLabel = label("doomed-");
        String keptLabel = label("kept-");

        String noteId = as(ALICE)
                .body(Map.of("title", "Survivor", "body", "content"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + doomedLabel)
                .then()
                .statusCode(204);
        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + keptLabel)
                .then()
                .statusCode(204);

        as(ALICE).when().delete("/v1/notes/" + noteId).then().statusCode(204);
        as(ALICE).when().delete("/v1/labels/" + doomedLabel).then().statusCode(204);

        as(ALICE)
                .body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("title", is("Survivor"))
                .body("body", is("content"))
                .body("labelIds", hasSize(1))
                .body("labelIds", contains(keptLabel));
    }

    @Test
    @DisplayName("deleting every label a trashed note carried still leaves the note restorable")
    void restoreWithAllLabelsGone() {
        String labelId = label("all-gone-");
        String noteId = as(ALICE)
                .body(Map.of("body", "no labels left"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);

        as(ALICE).when().delete("/v1/notes/" + noteId).then().statusCode(204);
        as(ALICE).when().delete("/v1/labels/" + labelId).then().statusCode(204);

        as(ALICE)
                .body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("body", is("no labels left"))
                .body("labelIds", hasSize(0));
    }
}
