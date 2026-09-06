package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-027: names are unique per user, compared case-insensitively after trimming. */
@QuarkusTest
class LabelUniquenessTest extends ApiTestBase {

    @Test
    @DisplayName("casing and surrounding whitespace do not make a new label")
    void caseAndWhitespaceCollide() {
        String base = "uniq" + UUID.randomUUID().toString().substring(0, 8);
        as(ALICE).body(Map.of("name", base)).when().post("/v1/labels").then().statusCode(201);

        for (String variant : List.of(base.toUpperCase(), "  " + base + "  ", mixedCase(base))) {
            as(ALICE)
                    .body(Map.of("name", variant))
                    .when()
                    .post("/v1/labels")
                    .then()
                    .statusCode(409)
                    .body("code", is("label_name_conflict"));
        }
    }

    @Test
    @DisplayName("two users may each hold the same label name")
    void namesAreScopedPerUser() {
        String name = "shared" + UUID.randomUUID().toString().substring(0, 8);
        as(ALICE).body(Map.of("name", name)).when().post("/v1/labels").then().statusCode(201);
        as(BOB).body(Map.of("name", name)).when().post("/v1/labels").then().statusCode(201);
    }

    @Test
    @DisplayName("renaming onto an existing name is refused")
    void renameCannotCollide() {
        String taken = "taken" + UUID.randomUUID().toString().substring(0, 8);
        String other = "other" + UUID.randomUUID().toString().substring(0, 8);
        as(ALICE).body(Map.of("name", taken)).when().post("/v1/labels").then().statusCode(201);
        String id = as(ALICE)
                .body(Map.of("name", other))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        as(ALICE)
                .body(Map.of("name", taken.toUpperCase()))
                .when()
                .patch("/v1/labels/" + id)
                .then()
                .statusCode(409)
                .body("code", is("label_name_conflict"));
    }

    @Test
    @DisplayName("renaming a label to its own name is allowed")
    void renameToOwnNameAllowed() {
        String name = "self" + UUID.randomUUID().toString().substring(0, 8);
        String id = as(ALICE)
                .body(Map.of("name", name))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE)
                .body(Map.of("name", name.toUpperCase()))
                .when()
                .patch("/v1/labels/" + id)
                .then()
                .statusCode(200)
                .body("name", is(name.toUpperCase()));
    }

    @Test
    @DisplayName("a concurrent race cannot produce two labels with the same name")
    void concurrentCreateRace() throws Exception {
        String name = "race" + UUID.randomUUID().toString().substring(0, 8);
        String token = tokenFor(ALICE);

        List<Callable<Response>> attempts = IntStream.range(0, 6)
                .mapToObj(i -> (Callable<Response>) () -> RestAssured.given()
                        .auth()
                        .oauth2(token)
                        .contentType(ContentType.JSON)
                        .accept("application/json, application/problem+json")
                        .body(Map.of("name", name))
                        .when()
                        .post("/v1/labels"))
                .toList();

        List<Response> responses;
        try (ExecutorService pool = Executors.newFixedThreadPool(6)) {
            responses = pool.invokeAll(attempts).stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }

        long created = responses.stream().filter(r -> r.statusCode() == 201).count();
        assertThat(created)
                .as("the unique index, not the application check, is what makes this safe")
                .isEqualTo(1);
        assertThat(responses.stream().filter(r -> r.statusCode() != 201)).allSatisfy(r -> assertThat(r.statusCode())
                .as("a lost race is a conflict, never a server fault")
                .isEqualTo(409));
    }

    private static String mixedCase(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(i % 2 == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
        }
        return out.toString();
    }
}
