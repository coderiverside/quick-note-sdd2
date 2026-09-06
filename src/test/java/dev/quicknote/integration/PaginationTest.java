package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-039: every listing is paginated, and the envelope tells the truth. */
@QuarkusTest
class PaginationTest extends ApiTestBase {

    private String seed(int count, String marker) {
        for (int i = 0; i < count; i++) {
            as(BOB).body(Map.of("body", marker + " entry " + i))
                    .when()
                    .post("/v1/notes")
                    .then()
                    .statusCode(201);
        }
        return marker;
    }

    @Test
    @DisplayName("totals and page count describe the filtered set")
    void totalsAreAccurate() {
        String marker = seed(7, "pg" + UUID.randomUUID().toString().substring(0, 6));

        as(BOB).queryParam("q", marker)
                .queryParam("size", 3)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .body("totalElements", org.hamcrest.Matchers.is(7))
                .body("totalPages", org.hamcrest.Matchers.is(3))
                .body("items", org.hamcrest.Matchers.hasSize(3));
    }

    @Test
    @DisplayName("nextPage is present until the last page, then null")
    void nextPageEndsAtTheLastPage() {
        String marker = seed(5, "np" + UUID.randomUUID().toString().substring(0, 6));

        String firstNext = as(BOB).queryParam("q", marker)
                .queryParam("size", 2)
                .queryParam("page", 0)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .extract()
                .path("nextPage");
        assertThat(firstNext).isNotNull();

        String lastNext = as(BOB).queryParam("q", marker)
                .queryParam("size", 2)
                .queryParam("page", 2)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .body("items", org.hamcrest.Matchers.hasSize(1))
                .extract()
                .path("nextPage");
        assertThat(lastNext).as("there is nothing after the last page").isNull();
    }

    @Test
    @DisplayName("no item appears on two pages of a collection that is not changing")
    void pagesDoNotOverlap() {
        String marker = seed(9, "ov" + UUID.randomUUID().toString().substring(0, 6));

        List<String> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            List<String> ids = as(BOB).queryParam("q", marker)
                    .queryParam("size", 3)
                    .queryParam("page", page)
                    .when()
                    .get("/v1/notes")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("items.id");
            seen.addAll(ids);
        }
        assertThat(seen).hasSize(9).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("a page beyond the end is empty, not an error")
    void pageBeyondTheEndIsEmpty() {
        String marker = seed(2, "be" + UUID.randomUUID().toString().substring(0, 6));
        as(BOB).queryParam("q", marker)
                .queryParam("size", 10)
                .queryParam("page", 5)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .body("items", org.hamcrest.Matchers.hasSize(0))
                .body("totalElements", org.hamcrest.Matchers.is(2));
    }

    @Test
    @DisplayName("the default page size is 25 and the maximum is 100")
    void defaultAndMaximum() {
        as(BOB).when().get("/v1/notes").then().statusCode(200).body("size", org.hamcrest.Matchers.is(25));
        as(BOB).queryParam("size", 100)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .body("size", org.hamcrest.Matchers.is(100));
        asSendingInvalid(BOB)
                .queryParam("size", 101)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("labels are paginated on the same terms")
    void labelsArePaginatedToo() {
        for (int i = 0; i < 4; i++) {
            as(BOB).body(Map.of("name", "pl-" + UUID.randomUUID().toString().substring(0, 8)))
                    .when()
                    .post("/v1/labels")
                    .then()
                    .statusCode(201);
        }
        as(BOB).queryParam("size", 2)
                .when()
                .get("/v1/labels")
                .then()
                .statusCode(200)
                .body("items", org.hamcrest.Matchers.hasSize(2))
                .body("size", org.hamcrest.Matchers.is(2));
    }
}
