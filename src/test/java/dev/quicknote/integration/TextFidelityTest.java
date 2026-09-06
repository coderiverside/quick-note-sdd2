package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.quicknote.support.ApiTestBase;

/** FR-042: what the user typed is what comes back. */
@QuarkusTest
class TextFidelityTest extends ApiTestBase {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "line one\nline two\n\nline four",
                "日本語のメモ",
                "مذكرة عربية",
                "emoji: 🎉🎊 family: 👨‍👩‍👧‍👦",
                "quotes \" ' ` and backslash \\ and slash /",
                "math: ∑ ∞ ≠ — en dash – ellipsis …",
                "tabs\tand\tmore\ttabs",
                "<script>alert('xss')</script>",
                "%_wildcards_% and 100% literal"
            })
    @DisplayName("content round-trips byte for byte")
    void roundTripsExactly(String content) {
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
    @DisplayName("a full-length note in a multi-byte script is accepted: limits count characters")
    void maximumLengthInMultiByteScript() {
        // 20,000 characters of a script that encodes to three bytes each is ~60 KB of UTF-8 and is
        // perfectly valid. Sizing the limit in bytes would wrongly reject this.
        String content = "日".repeat(20_000);
        String id = as(ALICE)
                .body(Map.of("body", content))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String returned =
                as(ALICE).when().get("/v1/notes/" + id).then().extract().path("body");
        assertThat(returned).hasSize(20_000).isEqualTo(content);
    }

    @Test
    @DisplayName("one character past the limit is refused, naming the field")
    void oneCharacterPastTheLimit() {
        asSendingInvalid(ALICE)
                .body(Map.of("body", "a".repeat(20_001)))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(400)
                .body("code", org.hamcrest.Matchers.is("validation_failed"))
                .body("errors[0].field", org.hamcrest.Matchers.is("body"));
    }
}
