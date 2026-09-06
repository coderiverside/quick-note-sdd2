package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-023: a note deleted by mistake comes back exactly as it was. */
@QuarkusTest
class TrashRestoreTest extends ApiTestBase {

    private String label() {
        return as(ALICE)
                .body(Map.of("name", "res-" + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("title, body, colour, and labels all survive a trash-and-restore cycle")
    void everythingSurvives() {
        String labelId = label();
        String id = as(ALICE)
                .body(Map.of(
                        "title",
                        "Recoverable",
                        "body",
                        "line one\nline two 日本語",
                        "color",
                        "teal",
                        "labelIds",
                        List.of(labelId)))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);
        as(ALICE)
                .body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("trashed", is(false))
                .body("trashedAt", nullValue())
                .body("title", is("Recoverable"))
                .body("body", is("line one\nline two 日本語"))
                .body("color", is("teal"))
                .body("labelIds", contains(labelId));

        List<String> active =
                as(ALICE).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(active).contains(id);
    }

    @Test
    @DisplayName("an archived note restores to archived, not into the default view")
    void archivedRestoresToArchived() {
        String id = as(ALICE)
                .body(Map.of("body", "was archived"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        as(ALICE)
                .body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("archived", is(true))
                .body("trashed", is(false));

        List<String> active =
                as(ALICE).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(active).as("restoring must not resurface an archived note").doesNotContain(id);
    }

    @Test
    @DisplayName("a restored note leaves the trash view")
    void restoredNoteLeavesTheTrash() {
        String id = as(ALICE)
                .body(Map.of("body", "round trip"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        assertThat(this.<List<String>>trashIds()).contains(id);
        as(ALICE)
                .body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);
        assertThat(this.<List<String>>trashIds()).doesNotContain(id);
    }

    @Test
    @DisplayName("a trashed note can be edited only after it is restored")
    void editAfterRestore() {
        String id = as(ALICE)
                .body(Map.of("body", "before"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        as(ALICE)
                .body(Map.of("body", "while trashed"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(409)
                .body("code", is("note_trashed"));

        as(ALICE)
                .body(Map.of("trashed", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);
        as(ALICE)
                .body(Map.of("body", "after restore"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("body", is("after restore"));
    }

    @SuppressWarnings("unchecked")
    private <T> T trashIds() {
        return (T) as(ALICE)
                .when()
                .get("/v1/trash?size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
    }
}
