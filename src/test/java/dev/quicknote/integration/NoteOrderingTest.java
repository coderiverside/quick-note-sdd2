package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-035: pinned first, then most recently updated first. */
@QuarkusTest
class NoteOrderingTest extends ApiTestBase {

    private String create(String title) {
        return as(BOB).body(Map.of("title", title, "body", "b"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    @DisplayName("pinned notes sort ahead of unpinned, and unpinning restores ordinary position")
    void pinnedFirst() {
        String oldest = create("ordering-oldest");
        String middle = create("ordering-middle");
        String newest = create("ordering-newest");

        List<Boolean> pins = as(BOB).when()
                .get("/v1/notes?size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.pinned");
        assertThat(pins).as("nothing is pinned yet").doesNotContain(true);

        as(BOB).body(Map.of("pinned", true))
                .when()
                .patch("/v1/notes/" + oldest)
                .then()
                .statusCode(200);

        List<String> ids =
                as(BOB).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(ids.get(0))
                .as("a pinned note leads regardless of when it was updated")
                .isEqualTo(oldest);

        as(BOB).body(Map.of("pinned", false))
                .when()
                .patch("/v1/notes/" + oldest)
                .then()
                .statusCode(200);

        List<String> afterUnpin =
                as(BOB).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(afterUnpin.get(0))
                .as("unpinning returns it to the ordinary most-recently-updated position, which it now holds")
                .isEqualTo(oldest);
        assertThat(afterUnpin).contains(middle, newest);
    }

    @Test
    @DisplayName("within a group, the most recently updated comes first")
    void mostRecentlyUpdatedFirst() {
        String first = create("recency-a");
        String second = create("recency-b");

        List<String> ids =
                as(BOB).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(ids.indexOf(second)).isLessThan(ids.indexOf(first));

        as(BOB).body(Map.of("body", "touched"))
                .when()
                .patch("/v1/notes/" + first)
                .then()
                .statusCode(200);

        List<String> afterTouch =
                as(BOB).when().get("/v1/notes?size=100").then().extract().path("items.id");
        assertThat(afterTouch.indexOf(first)).isLessThan(afterTouch.indexOf(second));
    }
}
