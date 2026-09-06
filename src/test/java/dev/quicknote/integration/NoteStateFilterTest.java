package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-033 and FR-034: the default view is active notes only; state is an explicit filter. */
@QuarkusTest
class NoteStateFilterTest extends ApiTestBase {

    private String create(String title) {
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
    @DisplayName("archived notes leave the default view and appear under the archived filter")
    void archivedFilter() {
        String id = create("filter-archived");
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);

        List<String> active =
                as(ALICE).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(active).doesNotContain(id);

        List<String> archived = as(ALICE)
                .when()
                .get("/v1/notes?state=archived&size=100")
                .then()
                .extract()
                .path("items.id");
        assertThat(archived).contains(id);
    }

    @Test
    @DisplayName("trashed notes appear only under the trashed filter")
    void trashedFilter() {
        String id = create("filter-trashed");
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        assertThat(idsOf("/v1/notes?size=100")).doesNotContain(id);
        assertThat(idsOf("/v1/notes?state=archived&size=100")).doesNotContain(id);
        assertThat(idsOf("/v1/notes?state=trashed&size=100")).contains(id);
    }

    private List<String> idsOf(String path) {
        return as(ALICE).when().get(path).then().statusCode(200).extract().path("items.id");
    }

    @Test
    @DisplayName("an unknown state filter is rejected, naming the permitted values")
    void unknownStateRejected() {
        asSendingInvalid(ALICE)
                .when()
                .get("/v1/notes?state=nonsense")
                .then()
                .statusCode(400)
                .body("code", is("validation_failed"))
                .body("errors[0].field", is("state"));
    }
}
