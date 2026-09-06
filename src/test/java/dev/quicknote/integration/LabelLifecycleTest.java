package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-026 and FR-031: renaming touches no note; deleting a label deletes no note. */
@QuarkusTest
class LabelLifecycleTest extends ApiTestBase {

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

    private String noteWith(String title, String labelId) {
        String id = as(ALICE)
                .body(Map.of("title", title, "body", "content of " + title))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().put("/v1/notes/" + id + "/labels/" + labelId).then().statusCode(204);
        return id;
    }

    @Test
    @DisplayName("renaming leaves note content untouched and resolves under the new name")
    void renameDoesNotTouchNotes() {
        String labelId = label("rename-");
        String noteId = noteWith("keeps its content", labelId);

        String newName = "renamed-" + UUID.randomUUID().toString().substring(0, 8);
        as(ALICE)
                .body(Map.of("name", newName))
                .when()
                .patch("/v1/labels/" + labelId)
                .then()
                .statusCode(200)
                .body("name", is(newName));

        as(ALICE)
                .when()
                .get("/v1/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("title", is("keeps its content"))
                .body("body", is("content of keeps its content"))
                .body("labelIds", contains(labelId));

        List<String> stillFiltered = as(ALICE)
                .when()
                .get("/v1/notes?labelId=" + labelId + "&size=100")
                .then()
                .extract()
                .path("items.id");
        assertThat(stillFiltered).contains(noteId);
    }

    @Test
    @DisplayName("deleting a label detaches it everywhere and deletes no note")
    void deleteDetachesWithoutDeletingNotes() {
        String labelId = label("doomed-");
        String keptLabel = label("kept-");
        String first = noteWith("first survivor", labelId);
        String second = noteWith("second survivor", labelId);
        as(ALICE)
                .when()
                .put("/v1/notes/" + first + "/labels/" + keptLabel)
                .then()
                .statusCode(204);

        as(ALICE).when().delete("/v1/labels/" + labelId).then().statusCode(204);

        as(ALICE)
                .when()
                .get("/v1/notes/" + first)
                .then()
                .statusCode(200)
                .body("title", is("first survivor"))
                .body("labelIds", contains(keptLabel));

        as(ALICE)
                .when()
                .get("/v1/notes/" + second)
                .then()
                .statusCode(200)
                .body("title", is("second survivor"))
                .body("labelIds", hasSize(0));
    }

    @Test
    @DisplayName("a deleted label is gone from the label list")
    void deletedLabelDisappears() {
        String labelId = label("vanishing-");
        as(ALICE).when().delete("/v1/labels/" + labelId).then().statusCode(204);

        List<String> ids =
                as(ALICE).when().get("/v1/labels?size=100").then().extract().path("items.id");
        assertThat(ids).doesNotContain(labelId);
    }

    @Test
    @DisplayName("labels do not go to the trash: deletion is immediate and permanent")
    void labelsAreNotTrashed() {
        String labelId = label("permanent-");
        as(ALICE).when().delete("/v1/labels/" + labelId).then().statusCode(204);
        as(ALICE)
                .body(Map.of("name", "revive"))
                .when()
                .patch("/v1/labels/" + labelId)
                .then()
                .statusCode(404)
                .body("code", is("label_not_found"));
    }
}
