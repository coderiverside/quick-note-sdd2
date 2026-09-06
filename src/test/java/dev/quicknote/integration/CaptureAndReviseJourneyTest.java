package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** User Story 1 end to end: create, list, read, update, delete. */
@QuarkusTest
class CaptureAndReviseJourneyTest extends ApiTestBase {

    @Test
    @DisplayName("a user captures an idea, revisits it, revises it, and removes it")
    void fullJourney() {
        String id = as(ALICE)
                .body(Map.of("title", "Groceries", "body", "milk, bread"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        List<String> ids = as(ALICE)
                .when()
                .get("/v1/notes?size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(ids).contains(id);

        as(ALICE)
                .when()
                .get("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("title", is("Groceries"))
                .body("body", is("milk, bread"));

        as(ALICE)
                .body(Map.of("body", "milk, bread, coffee"))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("body", is("milk, bread, coffee"));

        // The change survives a fresh read, not just the response to the write.
        as(ALICE)
                .when()
                .get("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .body("body", is("milk, bread, coffee"))
                .body("title", is("Groceries"));

        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        List<String> after = as(ALICE)
                .when()
                .get("/v1/notes?size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(after).doesNotContain(id);
    }
}
