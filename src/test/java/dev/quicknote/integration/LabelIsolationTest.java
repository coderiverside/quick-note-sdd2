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

/** FR-032: a label the caller does not own is reported as absent, never attached. */
@QuarkusTest
class LabelIsolationTest extends ApiTestBase {

    private String bobsLabel() {
        return as(BOB).body(
                        Map.of("name", "bobs-" + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String alicesNote() {
        return as(ALICE)
                .body(Map.of("body", "alice note"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("alice cannot attach bob's label to her own note")
    void cannotAttachAnothersLabel() {
        String noteId = alicesNote();
        String labelId = bobsLabel();

        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(404)
                .body("code", is("label_not_found"));

        as(ALICE).when().get("/v1/notes/" + noteId).then().body("labelIds", org.hamcrest.Matchers.hasSize(0));
    }

    @Test
    @DisplayName("alice cannot reference bob's label when creating a note")
    void cannotCreateWithAnothersLabel() {
        String labelId = bobsLabel();
        as(ALICE)
                .body(Map.of("body", "attempt", "labelIds", List.of(labelId)))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(404)
                .body("code", is("label_not_found"));
    }

    @Test
    @DisplayName("alice cannot rename or delete bob's label")
    void cannotModifyAnothersLabel() {
        String labelId = bobsLabel();
        as(ALICE)
                .body(Map.of("name", "hijacked"))
                .when()
                .patch("/v1/labels/" + labelId)
                .then()
                .statusCode(404)
                .body("code", is("label_not_found"));
        as(ALICE).when().delete("/v1/labels/" + labelId).then().statusCode(404);

        List<String> bobsLabels =
                as(BOB).when().get("/v1/labels?size=100").then().extract().path("items.id");
        assertThat(bobsLabels).as("bob's label survived alice's attempts").contains(labelId);
    }

    @Test
    @DisplayName("a label listing never contains another user's labels")
    void listingIsScopedToOwner() {
        String labelId = bobsLabel();
        List<String> alicesLabels =
                as(ALICE).when().get("/v1/labels?size=100").then().extract().path("items.id");
        assertThat(alicesLabels).doesNotContain(labelId);
    }
}
