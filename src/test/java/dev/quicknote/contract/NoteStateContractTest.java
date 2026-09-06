package dev.quicknote.contract;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** State changes are attribute writes on the note; no verb appears in any path. */
@QuarkusTest
class NoteStateContractTest extends ApiTestBase {

    private String create() {
        return as(ALICE)
                .body(Map.of("title", "t", "body", "b"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("pinning and unpinning are attribute writes")
    void pinAndUnpin() {
        String id = create();
        as(ALICE)
                .body(Map.of("pinned", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("pinned", is(true));
        as(ALICE)
                .body(Map.of("pinned", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("pinned", is(false));
    }

    @Test
    @DisplayName("archiving and unarchiving are attribute writes")
    void archiveAndUnarchive() {
        String id = create();
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("archived", is(true));
        as(ALICE)
                .body(Map.of("archived", false))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("archived", is(false));
    }

    @Test
    @DisplayName("archiving a pinned note clears the pin in the same response")
    void archivingClearsPin() {
        String id = create();
        as(ALICE)
                .body(Map.of("pinned", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("archived", is(true))
                .body("pinned", is(false));
    }

    @Test
    @DisplayName("archived and trashed are never both true")
    void archivedAndTrashedAreExclusive() {
        String id = create();
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);
        as(ALICE)
                .when()
                .get("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("trashed", is(true))
                .body("archived", is(false));
    }

    @Test
    @DisplayName("every palette colour is accepted")
    void everyPaletteColourAccepted() {
        for (String colour : new String[] {
            "default", "red", "orange", "yellow", "green", "teal", "blue", "dark_blue", "purple", "pink", "brown"
        }) {
            String id = create();
            as(ALICE)
                    .body(Map.of("color", colour))
                    .when()
                    .patch("/v1/notes/" + id)
                    .then()
                    .statusCode(200)
                    .body("color", is(colour));
        }
    }

    @Test
    @DisplayName("a colour outside the palette is refused, listing what is permitted")
    void nonPaletteColourRefused() {
        String id = create();
        asSendingInvalid(ALICE)
                .body(Map.of("color", "chartreuse"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(400)
                .body("code", is("note_color_invalid"))
                .body("detail", containsString("dark_blue"));
    }

    @Test
    @DisplayName("setting trashed true is equivalent to deleting the note")
    void trashedTrueTrashesTheNote() {
        String id = create();
        as(ALICE)
                .body(Map.of("pinned", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);

        as(ALICE)
                .body(Map.of("trashed", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("trashed", is(true))
                .body("pinned", is(false));

        as(ALICE)
                .when()
                .get("/v1/trash?size=100")
                .then()
                .statusCode(200)
                .body("items.id", org.hamcrest.Matchers.hasItem(id));
    }

    @Test
    @DisplayName("content and state change together in one request")
    void combinedUpdate() {
        String id = create();
        as(ALICE)
                .body(Map.of("title", "renamed", "color", "teal", "pinned", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("renamed"))
                .body("color", is("teal"))
                .body("pinned", is(true));
    }
}
