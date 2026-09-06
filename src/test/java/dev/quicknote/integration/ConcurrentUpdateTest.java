package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * The spec's concurrent-edit edge case.
 *
 * <p>Two overlapping writes may serialize or may collide. Either is acceptable; what is not
 * acceptable is a 500, a lost note, or an inconsistent collection. A collision must surface as the
 * contractual {@code note_version_conflict}, which tells the client to re-read and retry, rather than
 * as an opaque server fault.
 */
@QuarkusTest
class ConcurrentUpdateTest extends ApiTestBase {

    private static final int WRITERS = 8;

    @Test
    @DisplayName("overlapping updates leave the collection consistent and never fault")
    void concurrentUpdates() throws Exception {
        String id = as(ALICE)
                .body(Map.of("title", "contended", "body", "initial"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String token = tokenFor(ALICE);
        List<Callable<Response>> writes = IntStream.range(0, WRITERS)
                .mapToObj(i -> (Callable<Response>) () -> io.restassured.RestAssured.given()
                        .auth()
                        .oauth2(token)
                        .contentType(io.restassured.http.ContentType.JSON)
                        .accept("application/json, application/problem+json")
                        .body(Map.of("body", "write-" + i))
                        .when()
                        .patch("/v1/notes/" + id))
                .toList();

        List<Response> responses;
        try (ExecutorService pool = Executors.newFixedThreadPool(WRITERS)) {
            List<Future<Response>> futures = pool.invokeAll(writes);
            responses = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
        }

        for (Response response : responses) {
            assertThat(response.statusCode())
                    .as("a contended write is either applied or reported as a conflict, never a fault")
                    .isIn(200, 409);
            if (response.statusCode() == 409) {
                assertThat(response.jsonPath().getString("code")).isEqualTo("note_version_conflict");
            }
        }
        assertThat(responses).anySatisfy(r -> assertThat(r.statusCode()).isEqualTo(200));

        Response finalState = as(ALICE).when().get("/v1/notes/" + id);
        finalState.then().statusCode(200);

        String body = finalState.jsonPath().getString("body");
        assertThat(body)
                .as("the surviving state is one of the writes, not a blend of several")
                .isIn(IntStream.range(0, WRITERS).mapToObj(i -> "write-" + i).toList());

        assertThat(Instant.parse(finalState.jsonPath().getString("updatedAt")))
                .as("the last-updated timestamp reflects the surviving state")
                .isAfterOrEqualTo(Instant.parse(finalState.jsonPath().getString("createdAt")));
    }
}
