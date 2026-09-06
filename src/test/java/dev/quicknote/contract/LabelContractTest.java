package dev.quicknote.contract;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** The label endpoints, validated against the authored contract. */
@QuarkusTest
class LabelContractTest extends ApiTestBase {

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @DisplayName("201 with Location and a conformant body")
    void createsLabel() {
        String name = unique("work");
        as(ALICE)
                .body(Map.of("name", name))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .header("Location", containsString("/v1/labels/"))
                .body("id", notNullValue())
                .body("name", is(name))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("the listing returns a conformant page envelope")
    void listsLabels() {
        as(ALICE)
                .body(Map.of("name", unique("listed")))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201);
        as(ALICE)
                .when()
                .get("/v1/labels")
                .then()
                .statusCode(200)
                .body("items", notNullValue())
                .body("page", is(0))
                .body("size", is(25))
                .body("totalElements", greaterThanOrEqualTo(1));
    }

    @Test
    @DisplayName("renaming returns the new name")
    void renamesLabel() {
        String id = as(ALICE)
                .body(Map.of("name", unique("before")))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String renamed = unique("after");
        as(ALICE)
                .body(Map.of("name", renamed))
                .when()
                .patch("/v1/labels/" + id)
                .then()
                .statusCode(200)
                .body("name", is(renamed));
    }

    @Test
    @DisplayName("deleting answers 204")
    void deletesLabel() {
        String id = as(ALICE)
                .body(Map.of("name", unique("doomed")))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        as(ALICE).when().delete("/v1/labels/" + id).then().statusCode(204);
        as(ALICE)
                .body(Map.of("name", "x"))
                .when()
                .patch("/v1/labels/" + id)
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("a duplicate name is a conflict")
    void duplicateNameConflicts() {
        String name = unique("dup");
        as(ALICE).body(Map.of("name", name)).when().post("/v1/labels").then().statusCode(201);
        as(ALICE)
                .body(Map.of("name", name))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(409)
                .contentType("application/problem+json")
                .body("code", is("label_name_conflict"));
    }

    @Test
    @DisplayName("a blank or over-length name is refused, naming the field")
    void invalidNamesRefused() {
        asSendingInvalid(ALICE)
                .body(Map.of("name", "   "))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(400)
                .body("errors[0].field", is("name"));

        asSendingInvalid(ALICE)
                .body(Map.of("name", "a".repeat(51)))
                .when()
                .post("/v1/labels")
                .then()
                .statusCode(400)
                .body("errors[0].field", is("name"));
    }

    @Test
    @DisplayName("a label that does not exist is reported as absent")
    void unknownLabel() {
        as(ALICE)
                .body(Map.of("name", unique("x")))
                .when()
                .patch("/v1/labels/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("code", is("label_not_found"));
    }

    @Test
    @DisplayName("a malformed label identifier is rejected before any lookup")
    void malformedLabelId() {
        as(ALICE)
                .when()
                .delete("/v1/labels/not-a-uuid")
                .then()
                .statusCode(400)
                .body("code", is("malformed_identifier"))
                .body("errors[0].field", is("labelId"));
    }
}
