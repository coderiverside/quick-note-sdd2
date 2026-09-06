package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * The byte ceiling versus the character limit — the place this is easy to get wrong.
 *
 * <p>Sizing the transport limit near the content limit would reject valid notes in any script that
 * encodes above one byte per character, and would replace a field-naming {@code 400} with a fieldless
 * {@code 413}. Both failures are silent until a user in the wrong alphabet complains.
 */
@QuarkusTest
class PayloadLimitTest extends ApiTestBase {

    @Test
    @DisplayName("a full-length note in a four-byte script is accepted: limits count characters")
    void multiByteContentAtTheLimitIsAccepted() {
        // Each of these encodes to four bytes in UTF-8, so 20,000 of them is ~80 KB.
        String content = "𝔞".repeat(20_000);
        assertThat(content.codePointCount(0, content.length())).isEqualTo(20_000);

        String id = as(ALICE)
                .body(Map.of("body", content))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String returned = as(ALICE)
                .when()
                .get("/v1/notes/" + id)
                .then()
                .statusCode(200)
                .extract()
                .path("body");
        assertThat(returned).isEqualTo(content);
    }

    @Test
    @DisplayName("one character past the content limit is a 400 naming the field, never a 413")
    void overLengthIsAValidationFailureNotAPayloadRejection() {
        asSendingInvalid(ALICE)
                .body(Map.of("body", "a".repeat(20_001)))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", is("validation_failed"))
                .body("errors[0].field", is("body"))
                .body("errors[0].code", is("too_long"));
    }

    @Test
    @DisplayName("a body beyond the transport ceiling is refused at the edge and stores nothing")
    void oversizedBodyIsRefusedAtTheEdge() {
        int before = as(BOB).when().get("/v1/notes?size=100").then().extract().path("totalElements");

        // Well past the 256K ceiling, and past any legitimate note.
        String enormous = "x".repeat(400_000);
        int status = asSendingInvalid(BOB)
                .body(Map.of("body", enormous))
                .when()
                .post("/v1/notes")
                .statusCode();

        assertThat(status)
                .as("refused at the edge (413) or by validation (400) — never accepted")
                .isIn(400, 413);

        int after = as(BOB).when().get("/v1/notes?size=100").then().extract().path("totalElements");
        assertThat(after).as("an oversized body must write nothing at all").isEqualTo(before);
    }

    @Test
    @DisplayName("an oversized header set is rejected")
    void oversizedHeadersRejected() {
        var request = asSendingInvalid(ALICE);
        for (int i = 0; i < 40; i++) {
            request = request.header("X-Padding-" + i, "y".repeat(500));
        }
        int status = request.body(Map.of("body", "b")).when().post("/v1/notes").statusCode();
        assertThat(status)
                .as("headers beyond the configured maximum must not be accepted")
                .isNotIn(200, 201);
    }

    @Test
    @DisplayName("the configured ceiling leaves room for the largest legitimate note")
    void ceilingLeavesHeadroomForAMaximalNote() {
        // Title at its limit, body at its limit in a four-byte script, and a full label set.
        List<String> labelIds = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> as(ALICE)
                        .body(Map.of(
                                "name",
                                "hdr-" + java.util.UUID.randomUUID().toString().substring(0, 8)))
                        .when()
                        .post("/v1/labels")
                        .then()
                        .statusCode(201)
                        .extract()
                        .<String>path("id"))
                .toList();

        int status = as(ALICE)
                .body(Map.of(
                        "title", "t".repeat(200),
                        "body", "𝔞".repeat(20_000),
                        "labelIds", labelIds))
                .when()
                .post("/v1/notes")
                .statusCode();

        assertThat(status)
                .as("the largest note the contract permits must fit inside the transport ceiling")
                .isEqualTo(201);
    }
}
