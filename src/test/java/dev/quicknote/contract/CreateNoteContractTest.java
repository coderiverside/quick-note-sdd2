package dev.quicknote.contract;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** {@code POST /v1/notes}, validated against the authored contract. */
@QuarkusTest
class CreateNoteContractTest extends ApiTestBase {

    @Test
    @DisplayName("201 with Location and a schema-conformant body")
    void createsNote() {
        as(ALICE)
                .body(Map.of("title", "Groceries", "body", "milk, bread"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .header("Location", containsString("/v1/notes/"))
                .body("id", notNullValue())
                .body("title", is("Groceries"))
                .body("body", is("milk, bread"))
                .body("color", is("default"))
                .body("pinned", is(false))
                .body("archived", is(false))
                .body("trashed", is(false))
                .body("trashedAt", is(emptyOrNullString()))
                .body("labelIds", hasSize(0))
                .body("createdAt", notNullValue())
                .body("updatedAt", notNullValue());
    }

    @Test
    @DisplayName("a title alone, or a body alone, is enough")
    void oneOfTitleOrBodySuffices() {
        as(ALICE)
                .body(Map.of("title", "Title only"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201);
        as(ALICE)
                .body(Map.of("body", "Body only"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201);
    }

    @Test
    @DisplayName("neither title nor body is rejected with note_content_required")
    void emptyNoteRejected() {
        as(ALICE)
                .body(Map.of())
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("code", is("note_content_required"))
                .body("status", is(400))
                .body("type", notNullValue())
                .body("title", notNullValue());
    }

    @Test
    @DisplayName("whitespace-only content counts as empty")
    void whitespaceIsEmpty() {
        as(ALICE)
                .body(Map.of("title", "   ", "body", "\n\t "))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("note_content_required"));
    }

    @Test
    @DisplayName("an over-length title is rejected naming the field, never truncated")
    void overLongTitleRejected() {
        asSendingInvalid(ALICE)
                .body(Map.of("title", "x".repeat(201), "body", "ok"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("validation_failed"))
                .body("errors", hasSize(1))
                .body("errors[0].field", is("title"))
                .body("errors[0].code", is("too_long"));
    }

    @Test
    @DisplayName("an over-length body is rejected naming the field")
    void overLongBodyRejected() {
        asSendingInvalid(ALICE)
                .body(Map.of("body", "x".repeat(20001)))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("validation_failed"))
                .body("errors[0].field", is("body"));
    }

    @Test
    @DisplayName("a colour outside the palette is rejected, listing the permitted values")
    void invalidColourRejected() {
        asSendingInvalid(ALICE)
                .body(Map.of("body", "b", "color", "chartreuse"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("note_color_invalid"))
                .body("detail", not(emptyOrNullString()));
    }

    @Test
    @DisplayName("a palette colour and pinned flag are honoured")
    void colourAndPinHonoured() {
        as(ALICE)
                .body(Map.of("body", "b", "color", "dark_blue", "pinned", true))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .body("color", equalTo("dark_blue"))
                .body("pinned", is(true));
    }
}
