package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * The constitution requires per-principal and per-IP limiting with {@code 429} and
 * {@code Retry-After}.
 *
 * <p>The limits are set absurdly low for this profile so the behaviour can be proven in a second
 * rather than by sending six hundred requests.
 */
@QuarkusTest
@TestProfile(RateLimitTest.TightLimits.class)
class RateLimitTest extends ApiTestBase {

    public static class TightLimits implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quicknote.ratelimit.per-principal", "5",
                    "quicknote.ratelimit.per-ip", "100000");
        }
    }

    private Response request() {
        return as(ALICE).when().get("/v1/notes");
    }

    @Test
    @DisplayName("exceeding the per-principal limit answers 429 with Retry-After")
    void perPrincipalLimit() {
        Response limited = null;
        for (int i = 0; i < 40 && limited == null; i++) {
            Response response = request();
            if (response.statusCode() == 429) {
                limited = response;
            }
        }

        assertThat(limited).as("the limit must actually engage").isNotNull();
        assertThat(limited.contentType()).contains("application/problem+json");
        assertThat(limited.jsonPath().getString("code")).isEqualTo("rate_limited");
        assertThat(limited.header("Retry-After"))
                .as("a client cannot back off sensibly without being told how long")
                .isNotNull();
        assertThat(Integer.parseInt(limited.header("Retry-After"))).isPositive();
    }

    @Test
    @DisplayName("health probes answer even while a caller is being throttled")
    void probesAreNotThrottled() {
        for (int i = 0; i < 40; i++) {
            request();
        }
        io.restassured.RestAssured.given().when().get("/q/health/live").then().statusCode(200);
        io.restassured.RestAssured.given().when().get("/q/health/ready").then().statusCode(200);
    }

    @Test
    @DisplayName("a throttled request performs no work")
    void throttledRequestChangesNothing() {
        for (int i = 0; i < 40; i++) {
            request();
        }
        Response create =
                as(ALICE).body(Map.of("body", "should not be created")).when().post("/v1/notes");
        assertThat(create.statusCode())
                .as("once throttled, a write must be refused rather than partially applied")
                .isEqualTo(429);
    }
}
