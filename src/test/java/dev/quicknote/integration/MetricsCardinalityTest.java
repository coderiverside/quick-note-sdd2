package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * HTTP metrics are labelled by URI template, never by concrete path.
 *
 * <p>Labelling by concrete path gives one time series per note identifier. That is the unbounded
 * cardinality failure the constitution names explicitly: it degrades slowly, and by the time it is
 * noticed the metrics backend is already the problem.
 */
@QuarkusTest
class MetricsCardinalityTest extends ApiTestBase {

    private static final Pattern UUID_IN_URI =
            Pattern.compile("uri=\"[^\"]*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /** Prometheus exposition is text, not JSON, so the scrape asks for what it will actually get. */
    private String scrape() {
        return io.restassured.RestAssured.given()
                .auth()
                .oauth2(tokenFor(ALICE))
                .accept("text/plain, */*")
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();
    }

    @Test
    @DisplayName("no metric label contains a concrete resource identifier")
    void noIdentifiersInLabels() {
        // Touch several distinct note identifiers; if labels used concrete paths, each would appear.
        for (int i = 0; i < 3; i++) {
            String id = as(ALICE)
                    .body(Map.of("body", "metrics " + i))
                    .when()
                    .post("/v1/notes")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("id");
            as(ALICE).when().get("/v1/notes/" + id).then().statusCode(200);
        }
        as(ALICE).when().get("/v1/notes/" + UUID.randomUUID()).then().statusCode(404);

        String metrics = scrape();
        Matcher matcher = UUID_IN_URI.matcher(metrics);
        assertThat(matcher.find())
                .as(
                        "a per-identifier time series is exactly the unbounded cardinality to avoid; found: %s",
                        matcher.hitEnd() ? "none" : metrics.substring(matcher.start(), matcher.end()))
                .isFalse();
    }

    @Test
    @DisplayName("HTTP request metrics are actually being recorded")
    void metricsArePresent() {
        as(ALICE).when().get("/v1/notes").then().statusCode(200);
        String metrics = scrape();
        assertThat(metrics)
                .as("observability that records nothing is not observability")
                .contains("http_server_requests_seconds");
    }

    @Test
    @DisplayName("templated URIs appear in the labels")
    void templatedUrisAppear() {
        String id = as(ALICE)
                .body(Map.of("body", "template check"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().get("/v1/notes/" + id).then().statusCode(200);

        assertThat(scrape())
                .as("the template is what makes one series per route rather than one per resource")
                .contains("/v1/notes/{noteId}");
    }
}
