package dev.quicknote.contract;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** {@code GET /v1/notes?q=}, validated against the authored contract. */
@QuarkusTest
class SearchContractTest extends ApiTestBase {

    @Test
    @DisplayName("a search returns a conformant page envelope")
    void searchEnvelope() {
        String term = "conformant" + UUID.randomUUID().toString().substring(0, 8);
        as(ALICE)
                .body(Map.of("body", "a note about " + term))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201);

        as(ALICE)
                .when()
                .get("/v1/notes?q=" + term)
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("page", is(0))
                .body("size", is(25))
                .body("totalElements", is(1))
                .body("totalPages", is(1))
                .body("items[0].id", notNullValue());
    }

    @Test
    @DisplayName("a search matching nothing is an empty page, not an error")
    void noMatchesIsEmptyPage() {
        as(ALICE)
                .when()
                .get("/v1/notes?q=" + UUID.randomUUID())
                .then()
                .statusCode(200)
                .body("items", hasSize(0))
                .body("totalElements", is(0))
                .body("totalPages", is(0));
    }

    @Test
    @DisplayName("a page size above the maximum is rejected during search too")
    void oversizedPageRejected() {
        asSendingInvalid(ALICE)
                .when()
                .get("/v1/notes?q=anything&size=101")
                .then()
                .statusCode(400)
                .body("code", is("validation_failed"))
                .body("errors[0].field", is("size"));
    }
}
