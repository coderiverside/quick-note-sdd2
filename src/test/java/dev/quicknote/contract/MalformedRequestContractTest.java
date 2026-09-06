package dev.quicknote.contract;

import static org.hamcrest.Matchers.is;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** Malformed input is refused at the boundary, before anything reaches the service layer. */
@QuarkusTest
class MalformedRequestContractTest extends ApiTestBase {

    @Test
    @DisplayName("syntactically invalid JSON is rejected")
    void invalidJsonRejected() {
        asSendingInvalid(ALICE)
                .body("{\"title\": \"unterminated")
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("code", is("malformed_request"));
    }

    @Test
    @DisplayName("an unknown property is rejected, never silently persisted")
    void unknownPropertyRejected() {
        asSendingInvalid(ALICE)
                .body(Map.of("body", "b", "isAdmin", true))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("malformed_request"));
    }

    @Test
    @DisplayName("a property of the wrong JSON type is rejected")
    void wrongTypeRejected() {
        asSendingInvalid(ALICE)
                .body("{\"title\": 12345, \"pinned\": \"yes\"}")
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("malformed_request"));
    }

    @Test
    @DisplayName("no internal detail leaks in a malformed-request response")
    void noInternalDetailLeaks() {
        String body = asSendingInvalid(ALICE)
                .body("{\"body\": ")
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .extract()
                .asString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("com.fasterxml")
                .doesNotContain("dev.quicknote")
                .doesNotContain("at java.")
                .doesNotContain("Caused by");
    }

    @Test
    @DisplayName("a malformed body never reaches the service: nothing is created")
    void nothingIsCreated() {
        int before = as(BOB).when().get("/v1/notes?size=100").then().extract().path("totalElements");
        asSendingInvalid(BOB)
                .body("{\"title\": ")
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400);
        int after = as(BOB).when().get("/v1/notes?size=100").then().extract().path("totalElements");
        org.assertj.core.api.Assertions.assertThat(after).isEqualTo(before);
    }
}
