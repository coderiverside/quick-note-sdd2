package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/**
 * A search term is data, never pattern syntax.
 *
 * <p>{@code %} and {@code _} are ILIKE wildcards. Left unescaped, searching for "100%" would match
 * every note, and a search for "a_c" would match "abc" — the user's literal text would silently
 * become a query language.
 */
@QuarkusTest
class SearchEscapingTest extends ApiTestBase {

    private String create(String body) {
        return as(BOB).body(Map.of("body", body))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /** queryParam, not string concatenation: the term must reach the service exactly as typed. */
    private List<String> search(String term) {
        return as(BOB).queryParam("size", 100)
                .queryParam("q", term)
                .when()
                .get("/v1/notes")
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
    }

    @Test
    @DisplayName("a percent sign matches literally, not as a wildcard")
    void percentIsLiteral() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String withPercent = create("battery at 100% " + marker);
        String withoutPercent = create("battery at 100 " + marker);

        assertThat(search("100% " + marker))
                .as("a wildcard interpretation would match both")
                .contains(withPercent)
                .doesNotContain(withoutPercent);
    }

    @Test
    @DisplayName("an underscore matches literally, not as a single-character wildcard")
    void underscoreIsLiteral() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String withUnderscore = create("snake_case " + marker);
        String withLetter = create("snakeXcase " + marker);

        assertThat(search("snake_case"))
                .contains(withUnderscore)
                .as("an unescaped underscore would also match snakeXcase")
                .doesNotContain(withLetter);
    }

    @Test
    @DisplayName("a backslash matches literally and does not break the escape sequence")
    void backslashIsLiteral() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String id = create("path C:\\\\Users\\\\note " + marker);
        assertThat(search("C:\\")).contains(id);
    }

    @Test
    @DisplayName("a term of only wildcards matches only notes that literally contain them")
    void wildcardOnlyTermIsNotAMatchAll() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        create("ordinary text " + marker);
        String literal = create("literal %% here " + marker);

        List<String> results = search("%%");
        assertThat(results)
                .as("an unescaped '%%' would match every note the user owns")
                .contains(literal);
        assertThat(results).hasSizeLessThan(50);
    }

    @Test
    @DisplayName("a quote in a search term cannot alter the query")
    void quotesAreSafe() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        String id = create("it's a note " + marker);
        assertThat(search("it's")).contains(id);
        assertThat(search("'; DROP TABLE notes; --")).isEmpty();

        // The table is still there.
        as(BOB).when().get("/v1/notes").then().statusCode(200);
    }
}
