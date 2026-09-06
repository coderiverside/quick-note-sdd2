package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-040: search, state, label, and pagination apply together in one request. */
@QuarkusTest
class CombinedFilterTest extends ApiTestBase {

    private String label() {
        return as(ALICE)
                .body(Map.of("name", "combo-" + UUID.randomUUID().toString().substring(0, 8)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String note(String body, String labelId) {
        String id = as(ALICE)
                .body(Map.of("body", body))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        if (labelId != null) {
            as(ALICE)
                    .when()
                    .put("/v1/notes/" + id + "/labels/" + labelId)
                    .then()
                    .statusCode(204);
        }
        return id;
    }

    @Test
    @DisplayName("search and label filter intersect")
    void searchAndLabelIntersect() {
        String term = "intersect" + UUID.randomUUID().toString().substring(0, 6);
        String labelId = label();

        String both = note("has " + term, labelId);
        String labelOnly = note("no term here", labelId);
        String termOnly = note("has " + term, null);

        List<String> results = as(ALICE)
                .when()
                .get("/v1/notes?size=100&q=" + term + "&labelId=" + labelId)
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");

        assertThat(results).containsExactly(both).doesNotContain(labelOnly, termOnly);
    }

    @Test
    @DisplayName("search, label, and state apply together")
    void searchLabelAndState() {
        String term = "tri" + UUID.randomUUID().toString().substring(0, 6);
        String labelId = label();
        String archived = note("archived " + term, labelId);
        String active = note("active " + term, labelId);
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + archived)
                .then()
                .statusCode(200);

        List<String> archivedResults = as(ALICE)
                .when()
                .get("/v1/notes?size=100&state=archived&q=" + term + "&labelId=" + labelId)
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(archivedResults).containsExactly(archived);

        List<String> activeResults = as(ALICE)
                .when()
                .get("/v1/notes?size=100&state=active&q=" + term + "&labelId=" + labelId)
                .then()
                .extract()
                .path("items.id");
        assertThat(activeResults).containsExactly(active);
    }

    @Test
    @DisplayName("filters and pagination combine, and the total counts the filtered set")
    void filtersAndPaginationCombine() {
        String term = "paged" + UUID.randomUUID().toString().substring(0, 6);
        String labelId = label();
        for (int i = 0; i < 5; i++) {
            note("entry " + i + " " + term, labelId);
        }

        int total = as(ALICE)
                .when()
                .get("/v1/notes?q=" + term + "&labelId=" + labelId + "&size=2&page=0")
                .then()
                .statusCode(200)
                .body("items", org.hamcrest.Matchers.hasSize(2))
                .body("totalPages", org.hamcrest.Matchers.is(3))
                .extract()
                .path("totalElements");

        assertThat(total)
                .as("the total reflects the filtered set, not the whole collection")
                .isEqualTo(5);
    }
}
