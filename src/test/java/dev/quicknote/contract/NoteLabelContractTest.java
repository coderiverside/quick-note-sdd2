package dev.quicknote.contract;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** Attachment is a sub-resource write, idempotent in both directions. */
@QuarkusTest
class NoteLabelContractTest extends ApiTestBase {

    private String note() {
        return as(ALICE)
                .body(Map.of("title", "t", "body", "b"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String label() {
        return as(ALICE)
                .body(Map.of("name", "lbl-" + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("attaching is idempotent: 204 both times, one entry")
    void attachIsIdempotent() {
        String noteId = note();
        String labelId = label();

        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);
        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);

        as(ALICE)
                .when()
                .get("/v1/notes/" + noteId)
                .then()
                .statusCode(200)
                .body("labelIds", hasSize(1))
                .body("labelIds", contains(labelId));
    }

    @Test
    @DisplayName("detaching is idempotent: 204 whether or not the label was attached")
    void detachIsIdempotent() {
        String noteId = note();
        String labelId = label();

        as(ALICE)
                .when()
                .delete("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);
        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);
        as(ALICE)
                .when()
                .delete("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);
        as(ALICE)
                .when()
                .delete("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(204);

        as(ALICE).when().get("/v1/notes/" + noteId).then().body("labelIds", hasSize(0));
    }

    @Test
    @DisplayName("the whole label set can be replaced in one request")
    void replaceSet() {
        String noteId = note();
        List<String> labels = IntStream.range(0, 3).mapToObj(i -> label()).toList();

        as(ALICE)
                .body(Map.of("labelIds", labels))
                .when()
                .put("/v1/notes/" + noteId + "/labels")
                .then()
                .statusCode(200)
                .body("labelIds", hasSize(3));

        as(ALICE)
                .body(Map.of("labelIds", List.of(labels.get(0))))
                .when()
                .put("/v1/notes/" + noteId + "/labels")
                .then()
                .statusCode(200)
                .body("labelIds", contains(labels.get(0)));
    }

    @Test
    @DisplayName("the twenty-first label is refused as unprocessable")
    void labelCeiling() {
        String noteId = note();
        List<String> twenty = IntStream.range(0, 20).mapToObj(i -> label()).toList();
        as(ALICE)
                .body(Map.of("labelIds", twenty))
                .when()
                .put("/v1/notes/" + noteId + "/labels")
                .then()
                .statusCode(200)
                .body("labelIds", hasSize(20));

        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + label())
                .then()
                .statusCode(422)
                .contentType("application/problem+json")
                .body("code", is("note_label_limit_exceeded"));
    }

    @Test
    @DisplayName("attaching a label that does not exist is reported as absent")
    void unknownLabelIsAbsent() {
        as(ALICE)
                .when()
                .put("/v1/notes/" + note() + "/labels/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("label_not_found"));
    }

    @Test
    @DisplayName("a trashed note refuses label changes")
    void trashedNoteRefusesLabels() {
        String noteId = note();
        String labelId = label();
        as(ALICE).when().delete("/v1/notes/" + noteId).then().statusCode(204);

        as(ALICE)
                .when()
                .put("/v1/notes/" + noteId + "/labels/" + labelId)
                .then()
                .statusCode(409)
                .body("code", is("note_trashed"));
    }

    @Test
    @DisplayName("notes can be filtered by label")
    void filterByLabel() {
        String labelId = label();
        String tagged = note();
        String untagged = note();
        as(ALICE)
                .when()
                .put("/v1/notes/" + tagged + "/labels/" + labelId)
                .then()
                .statusCode(204);

        List<String> filtered = as(ALICE)
                .when()
                .get("/v1/notes?labelId=" + labelId + "&size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");

        org.assertj.core.api.Assertions.assertThat(filtered).contains(tagged).doesNotContain(untagged);
    }
}
