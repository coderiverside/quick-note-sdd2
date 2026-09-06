package dev.quicknote.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * FR-001 to FR-003: a credential is required, it is verified, and a failed verification never
 * reveals which check failed.
 */
@QuarkusTest
class AuthenticationTest extends ApiTestBase {

    private Response attempt(String bearer) {
        var request =
                given().accept("application/json, application/problem+json").contentType(ContentType.JSON);
        if (bearer != null) {
            request = request.header("Authorization", "Bearer " + bearer);
        }
        return request.when().get("/v1/notes");
    }

    @Test
    @DisplayName("no credential is refused")
    void noCredential() {
        attempt(null).then().statusCode(401).body("code", is("unauthenticated"));
    }

    @Test
    @DisplayName("a structurally malformed credential is refused")
    void malformedCredential() {
        attempt("not-a-jwt").then().statusCode(401).body("code", is("unauthenticated"));
        attempt("aaa.bbb.ccc").then().statusCode(401).body("code", is("unauthenticated"));
    }

    @Test
    @DisplayName("a credential whose signature does not verify is refused")
    void tamperedSignature() {
        String[] parts = tokenFor(ALICE).split("\\.");
        String tampered = parts[0] + "." + parts[1] + "." + flipLast(parts[2]);
        attempt(tampered).then().statusCode(401).body("code", is("unauthenticated"));
    }

    @Test
    @DisplayName("a credential with tampered claims is refused")
    void tamperedClaims() {
        String[] parts = tokenFor(ALICE).split("\\.");
        String forgedClaims = java.util.Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString("{\"sub\":\"someone-else\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        attempt(parts[0] + "." + forgedClaims + "." + parts[2])
                .then()
                .statusCode(401)
                .body("code", is("unauthenticated"));
    }

    @Test
    @DisplayName("every failure looks identical: which check failed is never disclosed")
    void failuresAreIndistinguishable() {
        String[] parts = tokenFor(ALICE).split("\\.");
        List<Response> failures = List.of(
                attempt(null),
                attempt("not-a-jwt"),
                attempt("aaa.bbb.ccc"),
                attempt(parts[0] + "." + parts[1] + "." + flipLast(parts[2])));

        for (Response failure : failures) {
            assertThat(failure.statusCode()).isEqualTo(401);
            assertThat(failure.jsonPath().getString("code")).isEqualTo("unauthenticated");
            assertThat(failure.jsonPath().getString("detail"))
                    .as("the detail must not name the failing check")
                    .isEqualTo("Authentication required.");
        }
        assertThat(failures.stream()
                        .map(r -> r.jsonPath().getString("detail"))
                        .distinct()
                        .count())
                .as("a distinguishable message would tell an attacker which part of the token to fix")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a valid credential is accepted")
    void validCredentialAccepted() {
        attempt(tokenFor(ALICE)).then().statusCode(200);
    }

    /**
     * Tampers in the middle, not at the end: the final base64url character encodes only part of a
     * byte, so flipping it can decode to the very same signature and prove nothing.
     */
    private static String flipLast(String value) {
        int at = value.length() / 2;
        char current = value.charAt(at);
        char replacement = current == 'A' ? 'B' : 'A';
        return value.substring(0, at) + replacement + value.substring(at + 1);
    }
}
