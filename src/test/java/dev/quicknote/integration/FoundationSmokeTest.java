package dev.quicknote.integration;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * Proves the foundation holds before any story is built on it: the probes are public, everything
 * else is denied by default, migrations applied, and tokens can be obtained for both seeded users.
 */
@QuarkusTest
class FoundationSmokeTest extends ApiTestBase {

    @Test
    @DisplayName("liveness and readiness are the only unauthenticated paths")
    void probesArePublic() {
        given().when().get("/q/health/live").then().statusCode(200).body("status", is("UP"));
        given().when().get("/q/health/ready").then().statusCode(200).body("status", is("UP"));
    }

    @Test
    @DisplayName("everything else is denied without a credential")
    void deniedByDefault() {
        anonymous().when().get("/v1/notes").then().statusCode(401).body("code", is("unauthenticated"));
        anonymous()
                .when()
                .get("/v1/notes/" + java.util.UUID.randomUUID())
                .then()
                .statusCode(401);
        // A well-formed create attempt: refused for want of a credential, not for its shape.
        anonymous()
                .contentType(io.restassured.http.ContentType.JSON)
                .body(java.util.Map.of("body", "b"))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(401)
                .body("code", is("unauthenticated"));
    }

    @Test
    @DisplayName("both seeded users can obtain a token, and they are different principals")
    void bothUsersAuthenticate() {
        String alice = tokenFor(ALICE);
        String bob = tokenFor(BOB);
        assertThat(alice).isNotBlank();
        assertThat(bob).isNotBlank().isNotEqualTo(alice);
    }

    @Test
    @DisplayName("the authored contract is present and loadable")
    void contractIsLoadable() {
        assertThat(dev.quicknote.support.ContractValidation.filter()).isNotNull();
    }
}
