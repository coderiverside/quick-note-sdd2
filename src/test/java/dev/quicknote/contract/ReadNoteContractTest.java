package dev.quicknote.contract;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** {@code GET /v1/notes} and {@code GET /v1/notes/{noteId}}. */
@QuarkusTest
class ReadNoteContractTest extends ApiTestBase {

    private String createNote(String title) {
        return as(ALICE)
                .body(Map.of("title", title, "body", "b"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("the listing returns a conformant page envelope")
    void listingEnvelope() {
        createNote("listed");
        as(ALICE)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .body("items", notNullValue())
                .body("page", is(0))
                .body("size", is(25))
                .body("totalElements", greaterThanOrEqualTo(1))
                .body("totalPages", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("a note is retrievable by its identifier")
    void retrieveById() {
        String id = createNote("retrievable");
        as(ALICE)
                .when()
                .get("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("id", is(id))
                .body("title", is("retrievable"));
    }

    @Test
    @DisplayName("a note that does not exist is reported as absent")
    void unknownNoteIsNotFound() {
        as(ALICE)
                .when()
                .get("/v1/notes/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("code", is("note_not_found"));
    }

    @Test
    @DisplayName("a malformed identifier is rejected before any lookup")
    void malformedIdentifierRejected() {
        as(ALICE)
                .when()
                .get("/v1/notes/not-a-uuid")
                .then()
                .statusCode(400)
                .body("code", is("malformed_identifier"))
                .body("errors[0].field", is("noteId"));
    }

    @Test
    @DisplayName("a page size above the maximum is rejected, not clamped")
    void oversizedPageRejected() {
        asSendingInvalid(ALICE)
                .when()
                .get("/v1/notes?size=101")
                .then()
                .statusCode(400)
                .body("code", is("validation_failed"))
                .body("errors[0].field", is("size"));
    }

    @Test
    @DisplayName("a listing that matches nothing is an empty page, not an error")
    void emptyListingIsNotAnError() {
        as(BOB).when()
                .get("/v1/notes?q=" + UUID.randomUUID())
                .then()
                .statusCode(200)
                .body("items", hasSize(0))
                .body("totalElements", is(0));
    }

    @Test
    @DisplayName("archived and trashed notes are excluded from the default listing")
    void defaultListingExcludesArchivedAndTrashed() {
        String archived = createNote("to-archive");
        String trashed = createNote("to-trash");
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + archived)
                .then()
                .statusCode(200);
        as(ALICE).when().delete("/v1/notes/" + trashed).then().statusCode(204);

        as(ALICE)
                .when()
                .get("/v1/notes?size=100")
                .then()
                .statusCode(200)
                .body("items.archived", everyItem(is(false)))
                .body("items.trashed", everyItem(is(false)));
    }
}
