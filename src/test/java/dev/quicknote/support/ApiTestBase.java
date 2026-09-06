package dev.quicknote.support;

import static io.restassured.RestAssured.given;

import java.net.URL;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.keycloak.client.KeycloakTestClient;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Shared harness for every integration and contract test.
 *
 * <p>Backed by real infrastructure — Dev Services provisions PostgreSQL and Keycloak — because
 * mocking either would test something this service does not do. {@code alice} and {@code bob} exist
 * so per-user isolation can be asserted rather than assumed.
 */
public abstract class ApiTestBase {

    public static final String ALICE = "alice";
    public static final String BOB = "bob";

    protected final KeycloakTestClient keycloak = new KeycloakTestClient();

    @TestHTTPResource
    URL baseUrl;

    /** A request authenticated as the given seeded user, with contract validation attached. */
    protected RequestSpecification as(String user) {
        return given().spec(new RequestSpecBuilder()
                        .addFilter(ContractValidation.filter())
                        .setContentType(ContentType.JSON)
                        .setAccept("application/json, application/problem+json")
                        .build())
                .auth()
                .oauth2(keycloak.getAccessToken(user));
    }

    /**
     * An authenticated request with no contract validation attached.
     *
     * <p>Needed in two situations: a request that deliberately violates the contract, where the
     * validator would abort the call before the service could reject it the way the contract says it
     * should; and a management endpoint such as {@code /q/metrics}, which is not part of the API
     * contract at all.
     */
    protected RequestSpecification unvalidated(String user) {
        return given().contentType(ContentType.JSON)
                .accept("application/json, application/problem+json")
                .auth()
                .oauth2(keycloak.getAccessToken(user));
    }

    /** Reads as intent at the call site: this request is meant to be rejected. */
    protected RequestSpecification asSendingInvalid(String user) {
        return unvalidated(user);
    }

    /** A request carrying no credential at all. */
    protected RequestSpecification anonymous() {
        return given().accept("application/json, application/problem+json");
    }

    protected String tokenFor(String user) {
        return keycloak.getAccessToken(user);
    }
}
