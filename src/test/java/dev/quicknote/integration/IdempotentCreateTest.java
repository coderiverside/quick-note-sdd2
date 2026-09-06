package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** Creation is the only non-idempotent operation in the contract, so it is the only one keyed. */
@QuarkusTest
class IdempotentCreateTest extends ApiTestBase {

    @Test
    @DisplayName("a replayed key returns the original response and creates no second note")
    void replayReturnsOriginal() {
        String key = UUID.randomUUID().toString();
        Map<String, Object> body = Map.of("title", "Retry me", "body", "b");

        String first = as(ALICE)
                .header("Idempotency-Key", key)
                .body(body)
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String replayed = as(ALICE)
                .header("Idempotency-Key", key)
                .body(body)
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        assertThat(replayed).isEqualTo(first);

        List<String> matching = as(ALICE)
                .when()
                .get("/v1/notes?q=Retry me&size=100")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(matching).containsExactly(first);
    }

    @Test
    @DisplayName("the same key from a different user creates a separate note")
    void keysAreScopedPerUser() {
        String key = UUID.randomUUID().toString();

        String aliceNote = as(ALICE)
                .header("Idempotency-Key", key)
                .body(Map.of("body", "alice content"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String bobNote = as(BOB).header("Idempotency-Key", key)
                .body(Map.of("body", "bob content"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        assertThat(bobNote)
                .as("one user's key must never reach another user's note")
                .isNotEqualTo(aliceNote);
        as(BOB).when()
                .get("/v1/notes/" + bobNote)
                .then()
                .statusCode(200)
                .body("body", org.hamcrest.Matchers.is("bob content"));
    }

    @Test
    @DisplayName("without a key, two identical requests create two notes")
    void unkeyedRequestsAreNotDeduplicated() {
        Map<String, Object> body = Map.of("body", "unkeyed " + UUID.randomUUID());
        String one = as(ALICE)
                .body(body)
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String two = as(ALICE)
                .body(body)
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        assertThat(one).isNotEqualTo(two);
    }
}
