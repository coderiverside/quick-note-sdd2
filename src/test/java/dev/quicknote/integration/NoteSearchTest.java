package dev.quicknote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.quicknote.support.ApiTestBase;

/** FR-036 and FR-037: case-insensitive substring across title and body; trash excluded. */
@QuarkusTest
class NoteSearchTest extends ApiTestBase {

    private String create(String title, String body) {
        return as(ALICE)
                .body(body == null ? Map.of("title", title) : Map.of("title", title, "body", body))
                .when()
                .post("/v1/notes")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private List<String> search(String term) {
        return as(ALICE)
                .when()
                .get("/v1/notes?size=100&q=" + term)
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
    }

    @Test
    @DisplayName("a term matches in the title and in the body alike")
    void matchesEitherField() {
        String term = "kumquat" + UUID.randomUUID().toString().substring(0, 6);
        String inTitle = create("about " + term, "unrelated body");
        String inBody = create("unrelated title", "mentions " + term + " here");
        create("neither", "nor");

        assertThat(search(term)).containsExactlyInAnyOrder(inTitle, inBody);
    }

    @Test
    @DisplayName("search is case-insensitive")
    void caseInsensitive() {
        String term = "MixedCase" + UUID.randomUUID().toString().substring(0, 6);
        String id = create("t", "contains " + term);

        assertThat(search(term.toLowerCase())).contains(id);
        assertThat(search(term.toUpperCase())).contains(id);
        assertThat(search(term)).contains(id);
    }

    @Test
    @DisplayName("search matches a substring, not only a whole word")
    void substringNotWholeWord() {
        String stem = "note" + UUID.randomUUID().toString().substring(0, 6);
        String id = create("t", "the word is " + stem + "book here");
        assertThat(search(stem))
                .as("substring semantics: searching 'note' must find 'notebook'")
                .contains(id);
    }

    @Test
    @DisplayName("trashed notes are excluded unless trashed notes are explicitly requested")
    void trashExcludedByDefault() {
        String term = "trashterm" + UUID.randomUUID().toString().substring(0, 6);
        String id = create("t", "holds " + term);
        as(ALICE).when().delete("/v1/notes/" + id).then().statusCode(204);

        assertThat(search(term)).doesNotContain(id);

        List<String> inTrash = as(ALICE)
                .when()
                .get("/v1/notes?state=trashed&size=100&q=" + term)
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(inTrash).contains(id);
    }

    @Test
    @DisplayName("archived notes are searchable under the archived filter")
    void archivedSearchable() {
        String term = "archterm" + UUID.randomUUID().toString().substring(0, 6);
        String id = create("t", "holds " + term);
        as(ALICE)
                .body(Map.of("archived", true))
                .when()
                .patch("/v1/notes/" + id)
                .then()
                .statusCode(200);

        assertThat(search(term)).doesNotContain(id);
        List<String> archived = as(ALICE)
                .when()
                .get("/v1/notes?state=archived&size=100&q=" + term)
                .then()
                .extract()
                .path("items.id");
        assertThat(archived).contains(id);
    }

    @Test
    @DisplayName("one user's search never reaches another user's notes")
    void searchIsScopedToOwner() {
        String term = "private" + UUID.randomUUID().toString().substring(0, 6);
        create("t", "alice writes " + term);

        List<String> bobsResults = as(BOB).when()
                .get("/v1/notes?size=100&q=" + term)
                .then()
                .statusCode(200)
                .extract()
                .path("items.id");
        assertThat(bobsResults).isEmpty();
    }

    @Test
    @DisplayName("a note with no body is searchable by title")
    void nullBodyIsSearchable() {
        String term = "titleonly" + UUID.randomUUID().toString().substring(0, 6);
        String id = create(term + " heading", null);
        assertThat(search(term)).contains(id);
    }
}
