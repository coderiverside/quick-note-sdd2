package dev.quicknote.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** The probes are the only public paths, and readiness actually checks something. */
@QuarkusTest
class HealthTest extends ApiTestBase {

    @Test
    @DisplayName("liveness and readiness answer without a credential")
    void probesArePublic() {
        given().when().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
        given().when().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
    }

    @Test
    @DisplayName("readiness verifies the database rather than merely reporting the process is alive")
    void readinessChecksDependencies() {
        given().when()
                .get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("checks.name", hasItem("quick-note dependencies"))
                .body("checks.find { it.name == 'quick-note dependencies' }.data.database", is("reachable"));
    }

    @Test
    @DisplayName("the probes are the only unauthenticated paths")
    void nothingElseIsPublic() {
        for (String path : new String[] {"/v1/notes", "/v1/labels", "/v1/trash", "/q/metrics", "/q/openapi"}) {
            int status = given().when().get(path).statusCode();
            assertThat(status)
                    .as("%s must not be reachable without a credential", path)
                    .isNotIn(200, 201, 204);
        }
    }

    @Test
    @DisplayName("the probes expose no user data")
    void probesExposeNothingSensitive() {
        as(ALICE)
                .body(java.util.Map.of("body", "health-secret-content"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201);

        String ready = given().when().get("/q/health/ready").then().extract().asString();
        assertThat(ready).doesNotContain("health-secret-content").doesNotContain("alice");
    }
}
